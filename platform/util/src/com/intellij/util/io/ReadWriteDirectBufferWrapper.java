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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
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
    final FileContext fileContext = new FileContext(myFile);
    try {
      final FileChannel channel = fileContext.myFile.getChannel();

      channel.position(myPosition);
      final ByteBuffer buffer = ByteBuffer.allocateDirect((int)myLength);
      channel.read(buffer);
      return buffer;
    }
    finally {
      //noinspection SSBasedInspection
      fileContext.dispose();
    }
  }

  static class FileContext implements Disposable {
    final RandomAccessFile myFile;

    FileContext(final File file) throws IOException {
      myFile = FileUtilRt.doIOOperation(new FileUtilRt.RepeatableIOOperation<RandomAccessFile, IOException>() {
        @Nullable
        @Override
        public RandomAccessFile execute(boolean finalAttempt) throws IOException {
          try {
            return new RandomAccessFile(file, RW);
          } catch (FileNotFoundException ex) {
            if (!file.getParentFile().exists()) {
              throw new IOException("Parent file doesn't exist:" + file);
            }
            if (!finalAttempt) return null;
            throw ex;
          }
        }
      });
    }

    @Override
    public void dispose() {
      try {
        if (myFile != null) myFile.close();
      } catch (IOException ex) {
        LOG.error(ex);
      }
    }
  }

  public <T extends Disposable> T flushWithContext(@Nullable T context) {
    final ByteBuffer buffer = getCachedBuffer();
    if (buffer == null || !isDirty()) return context;

    return doFlush((FileContext)context, buffer);
  }

  private <T extends Disposable> T doFlush(@Nullable FileContext fileContext, ByteBuffer buffer) {
    try {
      if (fileContext == null) fileContext = new FileContext(myFile);

      final FileChannel channel = fileContext.myFile.getChannel();

      channel.position(myPosition);
      buffer.rewind();
      channel.write(buffer);
      myDirty = false;
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return (T)fileContext;
  }

  @Override
  public void flush() {
    final ByteBuffer buffer = getCachedBuffer();
    if (buffer == null || !isDirty()) return;

    Disposable disposable = doFlush(null, buffer);
    if (disposable != null) {
      Disposer.dispose(disposable);
    }
  }
}
