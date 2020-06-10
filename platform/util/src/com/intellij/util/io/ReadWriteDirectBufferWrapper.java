// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Set;

import static com.intellij.util.io.FileChannelUtil.unInterruptible;

@ApiStatus.Internal
public class ReadWriteDirectBufferWrapper extends DirectBufferWrapper {
  private static final Logger LOG = Logger.getInstance(ReadWriteDirectBufferWrapper.class);
  private final boolean myReadOnly;

  protected ReadWriteDirectBufferWrapper(Path file, long offset, long length, boolean readOnly) {
    super(file, offset, length);
    assert length <= Integer.MAX_VALUE : length;
    myReadOnly = readOnly;
  }

  @Override
  protected ByteBuffer create() throws IOException {
    try (FileContext context = new FileContext(myFile, myReadOnly)) {
      FileChannel channel = context.myFile;
      assert channel != null;
      ByteBuffer buffer = ByteBuffer.allocateDirect((int)myLength);
      channel.read(buffer, myPosition);
      return buffer;
    }
  }

  static class FileContext implements AutoCloseable {
    private final FileChannel myFile;

    FileContext(Path path, boolean readOnly) throws IOException {
      myFile = FileUtilRt.doIOOperation(new FileUtilRt.RepeatableIOOperation<FileChannel, IOException>() {
        boolean parentWasCreated;

        @Nullable
        @Override
        public FileChannel execute(boolean finalAttempt) throws IOException {
          try {
            Set<StandardOpenOption> options = readOnly
                                              ? EnumSet.of(StandardOpenOption.READ)
                                              : EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
            return unInterruptible(FileChannel.open(path, options));
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
      IOUtil.closeSafe(LOG, myFile);
    }
  }

  FileContext flushWithContext(@Nullable FileContext fileContext) {
    ByteBuffer buffer = getCachedBuffer();
    if (buffer != null && isDirty()) {
      try {
        if (fileContext == null) {
          fileContext = new FileContext(myFile, myReadOnly);
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
    FileChannel channel = fileContext.myFile;
    assert channel != null;
    buffer.rewind();
    channel.write(buffer, myPosition);
    myDirty = false;
  }

  @Override
  public void flush() {
    ByteBuffer buffer = getCachedBuffer();
    if (buffer != null && isDirty()) {
      try (FileContext context = new FileContext(myFile, myReadOnly)) {
        doFlush(context, buffer);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  protected boolean isReadOnly() {
    return myReadOnly;
  }
}