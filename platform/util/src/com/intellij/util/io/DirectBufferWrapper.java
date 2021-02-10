// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import static com.intellij.util.io.FileChannelUtil.unInterruptible;

@ApiStatus.Internal
public final class DirectBufferWrapper {
  private static final Logger LOG = Logger.getInstance(DirectBufferWrapper.class);

  private final Path myFile;
  private final long myPosition;
  private final int myLength;
  private final boolean myReadOnly;

  private volatile ByteBuffer myBuffer;
  private volatile boolean myDirty;

  DirectBufferWrapper(Path file, long offset, int length, boolean readOnly) {
    myFile = file;
    myPosition = offset;
    myLength = length;
    myReadOnly = readOnly;
  }

  void markDirty() throws IOException {
    if (myReadOnly) {
      throw new IOException("Read-only byte buffer can't be modified. File: " + myFile);
    }
    if (!myDirty) myDirty = true;
  }

  final boolean isDirty() {
    return myDirty;
  }

  public ByteBuffer getCachedBuffer() {
    return myBuffer;
  }

  ByteBuffer getBuffer() throws IOException {
    ByteBuffer buffer = myBuffer;
    if (buffer == null) {
      myBuffer = buffer = DirectByteBufferAllocator.allocate(() -> create());
    }
    return buffer;
  }

  private ByteBuffer create() throws IOException {
    try (FileContext context = openContext()) {
      FileChannel channel = context.myFile;
      ByteBuffer buffer = ByteBuffer.allocateDirect(myLength);
      channel.read(buffer, myPosition);
      return buffer;
    }
  }

  void release() {
    if (isDirty()) flush();
    if (myBuffer != null) {
      ByteBufferUtil.cleanBuffer(myBuffer);
      myBuffer = null;
    }
  }

  void flushWithContext(@NotNull FileContext fileContext) throws IOException {
    ByteBuffer buffer = getCachedBuffer();
    if (buffer != null && isDirty()) {
      doFlush(fileContext, buffer);
    }
  }

  @NotNull
  FileContext openContext() throws IOException {
    return new FileContext(myFile, myReadOnly);
  }

  private void doFlush(FileContext fileContext, ByteBuffer buffer) throws IOException {
    FileChannel channel = fileContext.myFile;
    buffer.rewind();
    channel.write(buffer, myPosition);
    myDirty = false;
  }

  public void flush() {
    ByteBuffer buffer = getCachedBuffer();
    if (buffer != null && isDirty()) {
      try (FileContext context = openContext()) {
        doFlush(context, buffer);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  int getLength() {
    return myLength;
  }

  @Override
  public String toString() {
    return "Buffer for " + myFile + ", offset:" + myPosition + ", size: " + myLength;
  }

  public static DirectBufferWrapper readWriteDirect(@NotNull Path file, long offset, int length) {
    return new DirectBufferWrapper(file, offset, length, false);
  }

  public static DirectBufferWrapper readOnlyDirect(@NotNull Path file, long offset, int length) {
    return new DirectBufferWrapper(file, offset, length, true);
  }

  static class FileContext implements AutoCloseable {
    private final @NotNull FileChannel myFile;
    private final boolean myReadOnly;

    FileContext(Path path, boolean readOnly) throws IOException {
      myReadOnly = readOnly;
      myFile = Objects.requireNonNull(FileUtilRt.doIOOperation(finalAttempt -> {
        try {
          Set<StandardOpenOption> options = myReadOnly
                                            ? EnumSet.of(StandardOpenOption.READ)
                                            : EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
          return unInterruptible(FileChannel.open(path, options));
        }
        catch (NoSuchFileException ex) {
          Path parentFile = path.getParent();
          if (!Files.exists(parentFile)) {
            if (!Files.isWritable(path)) {
              throw ex;
            }
            Files.createDirectories(parentFile);
          }
          if (!finalAttempt) return null;
          throw ex;
        }
      }));
    }

    @Override
    public void close() {
      IOUtil.closeSafe(LOG, myFile);
    }
  }
}