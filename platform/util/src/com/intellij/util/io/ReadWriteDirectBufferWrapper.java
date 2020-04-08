// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static com.intellij.util.io.FileChannelUtil.unInterruptible;

public class ReadWriteDirectBufferWrapper extends DirectBufferWrapper {
  private static final Logger LOG = Logger.getInstance(ReadWriteDirectBufferWrapper.class);

  protected ReadWriteDirectBufferWrapper(Path file, final long offset, final long length) {
    super(file, offset, length);
    assert length <= Integer.MAX_VALUE : length;
  }

  @Override
  protected ByteBuffer create() throws IOException {
    try (FileContext context = new FileContext(myFile)) {
      FileChannel channel = context.file;
      assert channel != null;
      channel.position(myPosition);
      ByteBuffer buffer = ByteBuffer.allocateDirect((int)myLength);
      channel.read(buffer);
      return buffer;
    }
  }

  static class FileContext implements AutoCloseable {
    final FileChannel file;

    FileContext(Path path) throws IOException {
      file = FileUtilRt.doIOOperation(new FileUtilRt.RepeatableIOOperation<FileChannel, IOException>() {
        boolean parentWasCreated;

        @Nullable
        @Override
        public FileChannel execute(boolean finalAttempt) throws IOException {
          try {
            return unInterruptible(FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE));
          }
          catch (NoSuchFileException ex) {
            Path parentFile = path.getParent();
            if (!Files.exists(parentFile)) {
              if (!parentWasCreated) {
                FileUtil.createDirectory(parentFile.toFile());
                parentWasCreated = true;
              }
              else {
                throw new IOException("Parent directory still doesn't exist: " + path);
              }
            }
            if (!finalAttempt) return null;
            throw ex;
          }
        }
      });
    }

    @Override
    public void close() {
      try {
        if (file != null) file.close();
      }
      catch (IOException ex) {
        LOG.error(ex);
      }
    }
  }

  FileContext flushWithContext(@Nullable FileContext fileContext) {
    ByteBuffer buffer = getCachedBuffer();
    if (buffer != null && isDirty()) {
      try {
        if (fileContext == null) {
          fileContext = new FileContext(myFile);
        }
        doFlush(fileContext, buffer);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return fileContext;
  }

  private void doFlush(FileContext fileContext, ByteBuffer buffer) throws IOException {
    FileChannel channel = fileContext.file;
    assert channel != null;
    channel.position(myPosition);
    buffer.rewind();
    channel.write(buffer);
    myDirty = false;
  }

  @Override
  public void flush() {
    ByteBuffer buffer = getCachedBuffer();
    if (buffer != null && isDirty()) {
      try (FileContext context = new FileContext(myFile)) {
        doFlush(context, buffer);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }
}