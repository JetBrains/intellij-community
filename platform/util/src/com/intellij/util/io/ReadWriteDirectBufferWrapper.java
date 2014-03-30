/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class ReadWriteDirectBufferWrapper extends DirectBufferWrapper {
  @NonNls private static final String RW = "rw";

  protected ReadWriteDirectBufferWrapper(final File file, final long offset, final long length) {
    super(file, offset, length);
    assert length <= Integer.MAX_VALUE : length;
  }

  @Override
  protected ByteBuffer create() throws IOException {
    final RandomAccessFile file = createFile();
    try {
      final FileChannel channel = file.getChannel();
      try {
        channel.position(myPosition);
        final ByteBuffer buffer = ByteBuffer.allocateDirect((int)myLength);
        channel.read(buffer);
        return buffer;
      }
      finally {
        channel.close();
      }
    }
    finally {
      file.close();
    }
  }

  private RandomAccessFile createFile() throws FileNotFoundException {
    return FileUtilRt.doIOOperation(new FileUtilRt.RepeatableIOOperation<RandomAccessFile, FileNotFoundException>() {
      @Nullable
      @Override
      public RandomAccessFile execute(boolean finalAttempt) throws FileNotFoundException {
        try {
          return new RandomAccessFile(myFile, RW);
        } catch (FileNotFoundException ex) {
          if (!finalAttempt) return null;
          throw ex;
        }
      }
    });
  }

  @Override
  public void flush() {
    final ByteBuffer buffer = getCachedBuffer();
    if (buffer == null || !isDirty()) return;

    try {
      final RandomAccessFile file = createFile();
      try {
        final FileChannel channel = file.getChannel();
        try {
          channel.position(myPosition);
          buffer.rewind();
          channel.write(buffer);
          myDirty = false;
        }
        finally {
          channel.close();
        }
      }
      finally {
        file.close();
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }
}
