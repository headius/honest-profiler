/**
 * Copyright (c) 2014 Richard Warburton (richard.warburton@gmail.com)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 **/
package com.insightfullogic.honest_profiler.ports.sources;

import static java.nio.channels.FileChannel.MapMode.READ_ONLY;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import com.insightfullogic.honest_profiler.core.sources.CantReadFromSourceException;
import com.insightfullogic.honest_profiler.core.sources.LogSource;

/**
 * LogSource implementation which maintains a fixed-size memory-mapped window
 * ({@value #BUFFER_SIZE} bytes) on the file. The read() method checks whether a
 * certain amount of the current buffer ({@value #ELASTICITY} bytes) has been
 * read already, and if so, remaps the buffer to the new position.
 *
 * The ELASTICITY is provided for the benefit of the LogParser + Conductor
 * logic. If a partial record is read, the Conductor will sleep for a bit. So
 * ideally, BUFFER_SIZE - ELASTICITY should be large enough so that an "entire
 * record" (i.e. an entire stack) which might start at the last byte within the
 * ELASTICITY portion still would fit into the remains of the buffer.
 */
public class FileLogSource implements LogSource
{
    // Class Properties

    // Fixed buffer size
    private static final int BUFFER_SIZE = 1024 * 1024 * 5;
    // Remap if more than ELASTICITY has been read from the current buffer
    private static final int ELASTICITY = 1024 * 1024 * 4;

    // Instance Properties

    private final RandomAccessFile raf;
    private final FileChannel channel;
    private final File file;

    private MappedByteBuffer buffer;
    // the offset in the file where the current buffer starts.
    private long currentOffset = 0;

    // Instance Constructors

    public FileLogSource(final File file)
    {
        this.file = file;
        try
        {
            raf = new RandomAccessFile(file, "r");
            channel = raf.getChannel();
            mapBuffer(0);
        }
        catch (IOException e)
        {
            throw new CantReadFromSourceException(e);
        }
    }

    // Instance Accessors

    public File getFile()
    {
        return file;
    }

    // LogSource Implementation

    @Override
    public ByteBuffer read()
    {
        try
        {
            // If we've read more than ELASTICITY bytes, the
            // currentOffset is updated and the buffer is remapped.
            int position = buffer.position();
            if (position > ELASTICITY)
            {
                currentOffset += position;
                mapBuffer(currentOffset);
            }
        }
        catch (IOException e)
        {
            throw new CantReadFromSourceException(e);
        }

        return buffer;
    }

    @Override
    public void close() throws IOException
    {
        buffer = null;
        raf.close();
    }

    // Shame there's no simple abstraction for reading over both files and
    // network bytebuffers

    /**
     * Replaces the current buffer by a new ByteBuffer which is memory-mapped
     * onto a BUFFER_SIZE (10 MB at time of writing) window starting at the
     * specified offset.
     *
     * @throws IOException
     */
    private void mapBuffer(long offset) throws IOException
    {
        buffer = channel.map(READ_ONLY, offset, BUFFER_SIZE);
    }

    @Override
    public String toString()
    {
        return "FileLogSource{" + "file=" + file + '}';
    }
}
