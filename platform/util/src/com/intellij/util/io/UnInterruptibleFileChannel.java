// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.*;
import java.nio.file.OpenOption;
import java.nio.file.Path;

/**
 * A class intended to overcome interruptibility of {@link FileChannel}
 * by repeating passed operation until it will be successfully applied.
 *
 * If underlying {@link FileChannel} is observed in closed by interruption state,
 * in other words {@link ClosedByInterruptException} has been thrown we're trying to reopen it and apply operation again.
 */
@ApiStatus.Experimental
public final class UnInterruptibleFileChannel extends FileChannel {
  private final UnInterruptibleFileChannelHandle myFileChannelHandle;

  public UnInterruptibleFileChannel(@NotNull Path path, OpenOption @NotNull ... openOptions) throws IOException {
    myFileChannelHandle = new UnInterruptibleFileChannelHandle(path, openOptions);
  }

  @Override
  public int read(ByteBuffer dst) throws IOException {
    return myFileChannelHandle.executeOperation(ch -> ch.read(dst));
  }

  @Override
  public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
    return myFileChannelHandle.executeOperation(ch -> ch.read(dsts, offset, length));
  }

  @Override
  public int write(ByteBuffer src) throws IOException {
    return myFileChannelHandle.executeOperation(ch -> ch.write(src));
  }

  @Override
  public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
    return myFileChannelHandle.executeOperation(ch -> ch.write(srcs, offset, length));
  }

  @Override
  public long position() throws IOException {
    return myFileChannelHandle.executeOperation(ch -> ch.position());
  }

  @Override
  public FileChannel position(long newPosition) throws IOException {
    return myFileChannelHandle.executeOperation(ch -> ch.position(newPosition));
  }

  @Override
  public long size() throws IOException {
    return myFileChannelHandle.executeOperation(ch -> ch.size());
  }

  @Override
  public FileChannel truncate(long size) throws IOException {
    return myFileChannelHandle.executeOperation(ch -> ch.truncate(size));
  }

  @Override
  public void force(boolean metaData) throws IOException {
    myFileChannelHandle.executeOperation(ch -> {
      ch.force(metaData);
      return null;
    });

  }

  @Override
  public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int read(ByteBuffer dst, long position) throws IOException {
    return myFileChannelHandle.executeOperation(ch -> ch.read(dst, position));
  }

  @Override
  public int write(ByteBuffer src, long position) throws IOException {
    return myFileChannelHandle.executeOperation(ch -> ch.write(src, position));
  }

  @Override
  public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileLock lock(long position, long size, boolean shared) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileLock tryLock(long position, long size, boolean shared) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void implCloseChannel() throws IOException {
    myFileChannelHandle.close();
  }
}
