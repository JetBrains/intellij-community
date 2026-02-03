// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.io.FileChannelInterruptsRetryer.FileChannelIdempotentOperation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Class implements most of {@link FileChannel} operations so that each operation is either completed
 * successfully, or not started -- but operation (e.g. read or write) couldn't be partially applied.
 * Basically, it just reopens the underlying FileChannel, and repeats each operation on it until the
 * operation succeeds. Implementation mostly relies on already existing {@link FileChannelInterruptsRetryer}
 * machinery for that -- read {@link FileChannelInterruptsRetryer} description for implementation details
 * and discussions.
 * <p/>
 * This class could be seen as a counterpart for {@link FileChannelInterruptsRetryer} in following sense:
 * {@link FileChannelInterruptsRetryer} implements 'atomicity' (all-or-nothing) for logical unit of work
 * ({@link FileChannelIdempotentOperation}), while this class implements same 'atomicity' in relation to
 * elementary operations like read and write.
 * <p/>
 * All relative-positioned methods are guarded by 'this' lock -- this means that they are not concurrent
 * even if underlying FileChannel implementation and hardware allow parallel access. Use absolute
 * positioned methods if you're sure underlying impl support parallel access, and you want piggyback
 * on it.
 */
@ApiStatus.Internal
public final class ResilientFileChannel extends FileChannel {

  private final FileChannelInterruptsRetryer fileChannelHandle;
  /**
   * Keep position for relative-positioned operations -- reopened FileChannel lose its position,
   * so better have our own.
   * Position access/modification is protected by 'this' lock.
   */
  //@GuardedBy("this")
  private long position = 0;

  public ResilientFileChannel(final @NotNull Path path,
                              final OpenOption @NotNull ... openOptions) throws IOException {
    Set<OpenOption> openOptionsSet;
    if (openOptions.length == 0) {
      openOptionsSet = Collections.emptySet();
    }
    else {
      openOptionsSet = new HashSet<>();
      Collections.addAll(openOptionsSet, openOptions);
    }
    fileChannelHandle = new FileChannelInterruptsRetryer(path, openOptionsSet);
  }

  public ResilientFileChannel(final @NotNull Path path,
                              final Set<? extends @NotNull OpenOption> openOptions) throws IOException {
    fileChannelHandle = new FileChannelInterruptsRetryer(path, openOptions);
  }

  public <T> T executeOperation(final @NotNull FileChannelIdempotentOperation<T> operation) throws IOException {
    return fileChannelHandle.retryIfInterrupted(operation);
  }

  //FileChannelInterruptsRetryer requires idempotent operation -- so the operation could be repeated
  // multiple times without corrupting the data structure in the underlying file.
  // 1. Some operations are naturally idempotent (i.e. size)
  // 2. Absolute-position methods of FileChannel are also naturally idempotent
  // 3. All relative-position methods themselves are not idempotent -> to circumvent it, we keep .position
  //    in a local field (i.e. it is _detached_ from underlying FileChannel.position), and call apt absolute
  //    -positioned method instead

  @Override
  public long size() throws IOException {
    return fileChannelHandle.retryIfInterrupted(ch -> ch.size());
  }

  @Override
  public FileChannel truncate(final long size) throws IOException {
    synchronized (this) {
      this.position = Math.min(position, size);
    }
    return fileChannelHandle.retryIfInterrupted(ch -> ch.truncate(size));
  }

  @Override
  public void force(final boolean metaData) throws IOException {
    fileChannelHandle.retryIfInterrupted(ch -> {
      ch.force(metaData);
      return null;
    });
  }


  //RC: Could buffer.position/limit be 'corrupted' if operation is interrupted? It seems they could:
  //    i.e. it seems FileChannel operation interrupted in the middle could actually read all bytes
  //    in the buffer, and update position, but throw exception on the exit path (and tests seem to
  //    confirm such a behavior). This means we must store buffer.position before each .retryIfInterrupted()
  //    call and restore it inside lambda.

  @Override
  public int read(final ByteBuffer target,
                  final long offset) throws IOException {
    final int bufferPos = target.position();
    return fileChannelHandle.retryIfInterrupted(ch -> {
      target.position(bufferPos);
      return ch.read(target, offset);
    });
  }

  @Override
  public int write(final ByteBuffer source,
                   final long offset) throws IOException {
    final int bufferPos = source.position();
    return fileChannelHandle.retryIfInterrupted(ch -> {
      source.position(bufferPos);
      return ch.write(source, offset);
    });
  }

  @Override
  public MappedByteBuffer map(final MapMode mapMode,
                              final long mapRegionOffset,
                              final long mapRegionSize) throws IOException {
    return fileChannelHandle.retryIfInterrupted(ch -> ch.map(mapMode, mapRegionOffset, mapRegionSize));
  }

  @Override
  protected void implCloseChannel() throws IOException {
    fileChannelHandle.close();
  }

  //==================================================================================================
  //Relative-position methods: themselves not idempotent, implemented via absolute-positioned
  // methods, keeping .position in a local field (i.e. it is _detached_ from underlying FileChannel.position)
  //Relative-positioned methods are all synchronized(this) -- file position modification must be guarded
  // by lock.
  //==================================================================================================


  @Override
  public synchronized FileChannel position(final long newPosition) throws IOException {
    this.position = newPosition;
    return this;
  }

  @Override
  public synchronized int read(final ByteBuffer target) throws IOException {
    final int bytesRead = read(target, position);
    position += Math.max(0, bytesRead);
    return bytesRead;
  }

  @Override
  public synchronized int write(final ByteBuffer src) throws IOException {
    final int bytesWritten = write(src, position);
    position += Math.max(0, bytesWritten);
    return bytesWritten;
  }

  @Override
  public synchronized long position() {
    return position;
  }

  //==================================================================================================
  //Some methods could be implemented, but are not used, so implementation efforts not pay off now,
  //==================================================================================================
  //MAYBE RC: replace @Deprecated annotation with something more explicitly stating '@DoNotCall'?

  /** @deprecated method is not implemented due to constructive laziness */
  @Override
  @Deprecated
  @Contract("_, _, _ -> fail")
  public long read(ByteBuffer[] targets, int offset, int length) throws IOException {
    throw new UnsupportedOperationException("Method not implemented yet: no use");
  }

  /** @deprecated method is not implemented due to constructive laziness */
  @Override
  @Deprecated
  @Contract("_, _, _ -> fail")
  public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
    throw new UnsupportedOperationException("Method not implemented yet: no use");
  }

  /** @deprecated method is not implemented due to constructive laziness */
  @Override
  @Deprecated
  @Contract("_, _, _ -> fail")
  public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
    throw new UnsupportedOperationException("Method not implemented yet: no use");
  }

  /** @deprecated method is not implemented due to constructive laziness */
  @Override
  @Deprecated
  @Contract("_, _, _ -> fail")
  public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
    throw new UnsupportedOperationException("Method not implemented yet: no use");
  }

  /** @deprecated method is not implemented due to constructive laziness */
  @Override
  @Deprecated
  @Contract("_, _, _ -> fail")
  public FileLock lock(long position, long size, boolean shared) throws IOException {
    throw new UnsupportedOperationException("Method not implemented yet: no use");
  }

  /** @deprecated method is not implemented due to constructive laziness */
  @Override
  @Deprecated
  @Contract("_, _, _ -> fail")
  public FileLock tryLock(long position, long size, boolean shared) throws IOException {
    throw new UnsupportedOperationException("Method not implemented yet: no use");
  }
}
