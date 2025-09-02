// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.io.FileChannelInterruptsRetryer.FileChannelIdempotentOperation;
import com.intellij.util.io.stats.CachedChannelsStatistics;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.*;

import static java.nio.file.StandardOpenOption.*;

/**
 * Cache of {@link FileChannel}s.
 * Cache eviction policy is kind of FIFO -- the first channel cached is the first candidate to drop
 * from the cache, given it is not used right now.
 * <p>
 * Cache provides 2 ways to access FileChannel: {@link #executeOp(Path, FileChannelOperation, boolean)} and {@link #executeIdempotentOp(Path, FileChannelIdempotentOperation, boolean)}.
 * In a first method lambda supplied with {@link ResilientFileChannel} channel wrapper -- see its description for details
 * about that 'reliable' means there. In the second method lambda must be idempotent, but supplied with direct {@link FileChannel}
 * without wrapping.
 */
@ApiStatus.Internal
public final class OpenChannelsCache { // TODO: Will it make sense to have a background thread, that flushes the cache by timeout?
  private final int myCapacity;
  private int myHitCount;
  private int myMissCount;
  private int myLoadCount;

  //@GuardedBy("myCacheLock")
  private final @NotNull Map<Path, ChannelDescriptor> myCache;

  private final transient Object myCacheLock = new Object();

  @VisibleForTesting
  public OpenChannelsCache(final int capacity) {
    myCapacity = capacity;
    myCache = new LinkedHashMap<>(capacity, 0.5f, true);
  }

  @NotNull CachedChannelsStatistics getStatistics() {
    synchronized (myCacheLock) {
      return new CachedChannelsStatistics(myHitCount, myMissCount, myLoadCount, myCapacity);
    }
  }

  @FunctionalInterface
  public interface FileChannelOperation<T> {
    T execute(@NotNull ResilientFileChannel channel) throws IOException;
  }

  /**
   * Note: implementation supplies {@link ResilientFileChannel} to processor. {@link ResilientFileChannel}
   * is a FileChannel implementation that tries to ensure each FileChannel operation is completed,
   * or not started at all, but not interrupted in the middle. If something interrupts 'elementary'
   * FileChannel ops, like read/write -- those ops are retried, invisibly for processor -- see class
   * description for details. But it comes with small performance cost, and als the {@link ResilientFileChannel}
   * does not implement some FileChannel operations, so be aware.
   */
  @VisibleForTesting
  public <T> T executeOp(final @NotNull Path path,
                         final @NotNull FileChannelOperation<T> operation,
                         final boolean read) throws IOException {
    ChannelDescriptor descriptor;
    synchronized (myCacheLock) {
      descriptor = myCache.get(path);
      if (descriptor == null) {
        boolean somethingDropped = releaseOverCachedChannels();
        descriptor = new ChannelDescriptor(path, read);
        myCache.put(path, descriptor);
        if (somethingDropped) {
          myMissCount++;
        }
        else {
          myLoadCount++;
        }
      }
      else if (!read && descriptor.isReadOnly()) {
        if (descriptor.isLocked()) {
          descriptor = new ChannelDescriptor(path, false);
        }
        else {
          // re-open as write
          closeChannel(path);
          descriptor = new ChannelDescriptor(path, false);
          myCache.put(path, descriptor);
        }
        myMissCount++;
      }
      else {
        myHitCount++;
      }
      descriptor.lock();
    }

    //channel access is NOT guarded by the myCacheLock
    try {
      return operation.execute(descriptor.channel());
    }
    finally {
      synchronized (myCacheLock) {
        descriptor.unlock();
      }
    }
  }

  /**
   * Parameter {@param operation} should be idempotent because sometimes calculation might be restarted
   * when file channel was closed by thread interruption
   */
  @VisibleForTesting
  public <T> T executeIdempotentOp(final @NotNull Path path,
                            final @NotNull FileChannelIdempotentOperation<T> operation,
                            final boolean read) throws IOException {
    ChannelDescriptor descriptor;
    synchronized (myCacheLock) {
      descriptor = myCache.get(path);
      if (descriptor == null) {
        boolean somethingDropped = releaseOverCachedChannels();
        descriptor = new ChannelDescriptor(path, read);
        myCache.put(path, descriptor);
        if (somethingDropped) {
          myMissCount++;
        }
        else {
          myLoadCount++;
        }
      }
      else if (!read && descriptor.isReadOnly()) {
        if (descriptor.isLocked()) {
          descriptor = new ChannelDescriptor(path, false);
        }
        else {
          // re-open as write
          closeChannel(path);
          descriptor = new ChannelDescriptor(path, false);
          myCache.put(path, descriptor);
        }
        myMissCount++;
      }
      else {
        myHitCount++;
      }
      descriptor.lock();
    }

    //channel access is NOT guarded by the myCacheLock
    try {
      return descriptor.channel().executeOperation(operation);
    }
    finally {
      synchronized (myCacheLock) {
        descriptor.unlock();
      }
    }
  }

  @VisibleForTesting
  public void closeChannel(Path path) throws IOException {
    synchronized (myCacheLock) {
      final ChannelDescriptor descriptor = myCache.remove(path);

      if (descriptor != null) {
        assert !descriptor.isLocked() : "Channel is in use: " + descriptor;
        descriptor.close();
      }
    }
  }

  private boolean releaseOverCachedChannels() throws IOException {
    int dropCount = myCache.size() - myCapacity;

    if (dropCount >= 0) {
      List<Path> keysToDrop = new ArrayList<>();
      for (Map.Entry<Path, ChannelDescriptor> entry : myCache.entrySet()) {
        if (dropCount < 0) break;
        if (!entry.getValue().isLocked()) {
          dropCount--;
          keysToDrop.add(entry.getKey());
        }
      }

      for (Path file : keysToDrop) {
        closeChannel(file);
      }

      return true;
    }
    return false;
  }

  static final class ChannelDescriptor implements Closeable {
    private static final OpenOption[] MODIFIABLE_OPTS = {READ, WRITE, CREATE};
    private static final OpenOption[] READ_ONLY_OPTS = {READ};

    private int lockCount = 0;
    private final @NotNull ResilientFileChannel channel;
    private final boolean readOnly;


    ChannelDescriptor(@NotNull Path file, boolean readOnly) throws IOException {
      this.readOnly = readOnly;
      channel = Objects.requireNonNull(FileUtilRt.doIOOperation(lastAttempt -> {
        try {
          return new ResilientFileChannel(file, readOnly ? READ_ONLY_OPTS : MODIFIABLE_OPTS);
        }
        catch (NoSuchFileException ex) {
          Path parent = file.getParent();
          if (!readOnly) {
            if (!Files.exists(parent)) {
              Files.createDirectories(parent);
            }
            if (!lastAttempt) return null;
          }
          throw ex;
        }
      }));
    }

    boolean isReadOnly() {
      return readOnly;
    }

    void lock() {
      lockCount++;
    }

    void unlock() {
      lockCount--;
    }

    boolean isLocked() {
      return lockCount != 0;
    }

    @NotNull ResilientFileChannel channel() {
      return channel;
    }

    @Override
    public void close() throws IOException {
      channel.close();
    }

    @Override
    public String toString() {
      return "ChannelDescriptor{" +
             "locks=" + lockCount +
             ", channel=" + channel +
             ", readOnly=" + readOnly +
             '}';
    }
  }
}
