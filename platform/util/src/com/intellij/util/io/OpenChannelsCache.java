// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.io.FileChannelInterruptsRetryer.FileChannelIdempotentOperation;
import com.intellij.util.io.stats.CachedChannelsStatistics;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owner of a limited shared cache of opened {@link FileChannel}s.
 * Cache eviction policy is kind of LRU -- the oldest channel accessed is the first to be evicted, given it is not used right now.
 * <p>
 * The owner exposes two mode-bound {@link ChannelsAccessor} views: {@link #asReadOnly()} and {@link #asWritable()}.
 * BEWARE: cache caches (potentially) 2 different {@linkplain FileChannel} instances: readOnly and !readOnly.
 * Generally, it is not guaranteed these 2 different FileChannels instances always share the same data -- they
 * could, but also there could be some temporary difference in the content visible via readOnly and !readOnly
 * FileChannel. So, better avoid accessing the same path via 2 different readOnly/!readOnly FileChannels: use
 * the single accessor for _all_ the accesses to the given Path.
 */
@ApiStatus.Internal
public final class OpenChannelsCache {
  // TODO: does it make sense to have a background thread, that flushes the cache by timeout?

  /** for {@linkplain #toString()} */
  private final String cacheName;

  /** Max channels to keep open in cache */
  private final int capacity;

  //@GuardedBy("cacheLock")
  private final @NotNull Map<CacheKey, ChannelDescriptor> cachedChannels;

  private final transient Object cacheLock = new Object();

  private final @NotNull ChannelsAccessor.FileChannelOpener channelOpener;

  private final transient @NotNull ChannelsAccessor readOnlyAccessor;
  private final transient @NotNull ChannelsAccessor writableAccessor;


  //statistics of the caching efficacy:
  private final PerModeStatistics readOnlyStats = new PerModeStatistics();
  private final PerModeStatistics writableStats = new PerModeStatistics();


  /** @param cacheName just for debugging */
  public OpenChannelsCache(@NotNull String cacheName,
                           int capacity,
                           @NotNull ChannelsAccessor.FileChannelOpener channelOpener) {
    this.cacheName = cacheName;
    this.capacity = capacity;
    cachedChannels = new LinkedHashMap<>(capacity, 0.5f, /*orderByAccess: */true);
    this.channelOpener = channelOpener;
    readOnlyAccessor = new AccessorView(/*readOnly: */true);
    writableAccessor = new AccessorView(/*readOnly: */false);
  }

  public @NotNull ChannelsAccessor asReadOnly() {
    return readOnlyAccessor;
  }

  public @NotNull ChannelsAccessor asWritable() {
    return writableAccessor;
  }

  public @NotNull CachedChannelsStatistics getStatistics() {
    synchronized (cacheLock) {
      return new CachedChannelsStatistics(
        readOnlyStats.hitCount + writableStats.hitCount,
        readOnlyStats.missCount + writableStats.missCount,
        readOnlyStats.loadCount + writableStats.loadCount,
        /*bypassedCache: */0,
        capacity
      );
    }
  }

  @Override
  public String toString() {
    return "OpenChannelsCache[" + cacheName + "]" +
           "[capacity: " + capacity + ", cached: " + cachedChannels.size() + ", opener: " + channelOpener + "]";
  }

  /**
   * Note: this implementation supplies {@link ResilientFileChannel} to processor. {@link ResilientFileChannel}
   * is a FileChannel implementation that tries to ensure each FileChannel operation is completed,
   * or not started at all, but not interrupted in the middle. If something interrupts 'elementary'
   * FileChannel ops, like read/write -- those ops are retried, invisibly for processor -- see class
   * description for details. But it comes with small performance cost, and also the {@link ResilientFileChannel}
   * does not implement some FileChannel operations, so be aware.
   */
  private <T> T executeOp(@NotNull Path path,
                          @NotNull ChannelsAccessor.FileChannelOperation<T> operation,
                          boolean readOnly) throws IOException {
    ChannelDescriptor descriptor = acquireDescriptor(path, readOnly);
    //channel access is NOT guarded by the cacheLock
    try {
      return operation.execute(descriptor.channel());
    }
    finally {
      releaseDescriptor(descriptor);
    }
  }

  /**
   * Parameter {@param operation} should be idempotent because sometimes calculation might be restarted
   * when the file channel was closed by thread interruption
   */
  private <T> T executeIdempotentOp(@NotNull Path path,
                                    @NotNull FileChannelIdempotentOperation<T> operation,
                                    boolean readOnly) throws IOException {
    ChannelDescriptor descriptor = acquireDescriptor(path, readOnly);
    //channel access is NOT guarded by the cacheLock
    try {
      return descriptor.executeIdempotentOp(operation);
    }
    finally {
      releaseDescriptor(descriptor);
    }
  }

  private @NotNull ChannelDescriptor acquireDescriptor(@NotNull Path path,
                                                       boolean readOnly) throws IOException {
    synchronized (cacheLock) {
      CacheKey key = new CacheKey(path, readOnly);
      ChannelDescriptor descriptor = cachedChannels.get(key);
      PerModeStatistics statistics = statisticsFor(readOnly);
      if (descriptor == null) {
        boolean somethingDropped = releaseOverCachedChannels();
        descriptor = new ChannelDescriptor(path, readOnly, channelOpener);
        cachedChannels.put(key, descriptor);
        if (somethingDropped) {
          statistics.missCount++;
        }
        else {
          statistics.loadCount++;
        }
      }
      else {
        statistics.hitCount++;
      }
      descriptor.lock();
      return descriptor;
    }
  }

  private @NotNull OpenChannelsCache.PerModeStatistics statisticsFor(boolean readOnly) {
    return readOnly ? readOnlyStats : writableStats;
  }

  private void releaseDescriptor(@NotNull ChannelDescriptor descriptor) {
    synchronized (cacheLock) {
      descriptor.unlock();
    }
  }

  private void closeChannel(@NotNull Path path,
                            boolean readOnly) throws IOException {
    synchronized (cacheLock) {
      closeChannel(new CacheKey(path, readOnly));
    }
  }

  //@GuardedBy(cacheLock)
  private boolean releaseOverCachedChannels() throws IOException {
    int dropCount = cachedChannels.size() - capacity;

    if (dropCount < 0) {
      return false;
    }

    List<CacheKey> keysToDrop = new ArrayList<>();
    for (Map.Entry<CacheKey, ChannelDescriptor> entry : cachedChannels.entrySet()) {
      if (dropCount < 0) break;
      if (!entry.getValue().isLocked()) {
        dropCount--;
        keysToDrop.add(entry.getKey());
      }
    }

    for (CacheKey key : keysToDrop) {
      closeChannel(key);
    }

    return true;
  }

  //@GuardedBy(cacheLock)
  private void closeChannel(@NotNull OpenChannelsCache.CacheKey key) throws IOException {
    ChannelDescriptor descriptor = cachedChannels.remove(key);

    if (descriptor != null) {
      assert !descriptor.isLocked() : "Channel is in use: " + descriptor;
      descriptor.close();
    }
  }

  static final class ChannelDescriptor implements Closeable {
    private int lockCount = 0;
    private final @NotNull FileChannel channel;
    private final boolean readOnly;

    ChannelDescriptor(@NotNull Path path,
                      boolean readOnly,
                      @NotNull ChannelsAccessor.FileChannelOpener channelOpener) throws IOException {
      this.readOnly = readOnly;
      if (!readOnly) {
        Path parent = path.getParent();
        boolean parentExists = Files.exists(parent);
        if (!parentExists) {
          Files.createDirectories(parent);
        }
      }

      this.channel = Objects.requireNonNull(FileUtilRt.doIOOperation(isLastAttempt -> {
        try {
          return channelOpener.open(path, readOnly);
        }
        catch (NoSuchFileException ex) {
          if (!isLastAttempt) {
            return null;
          }

          //provide more diagnostic info:
          Path parent = path.getParent();
          boolean parentExists = Files.exists(parent);

          NoSuchFileException exception = new NoSuchFileException(
            path.toString(), /*other: */ null,
            "[" + path + "][readOnly: " + readOnly + "]: file doesn't exist, " +
            "parent [" + parent + "] " + (parentExists ? "does exist" : "doesn't exist")
          );
          exception.addSuppressed(ex);
          throw exception;
        }
      }));

      if (!(channel instanceof Resilient)) {
        throw new AssertionError("channel must be instanceof Resilient, but " + channel.getClass());
      }
    }

    private void lock() {
      lockCount++;
    }

    private void unlock() {
      lockCount--;
    }

    private boolean isLocked() {
      return lockCount != 0;
    }

    @NotNull FileChannel channel() {
      return channel;
    }

    <R> R executeIdempotentOp(@NotNull FileChannelIdempotentOperation<R> operation) throws IOException {
      return ((Resilient)channel).executeOperation(operation);
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

  private final class AccessorView implements ChannelsAccessor, DiagnosticChannelsAccessor {
    private final boolean readOnly;

    private AccessorView(boolean readOnly) {
      this.readOnly = readOnly;
    }

    @Override
    public boolean isReadOnly() {
      return readOnly;
    }

    @Override
    public <T> T executeOp(@NotNull Path path,
                           @NotNull FileChannelOperation<T> operation) throws IOException {
      return OpenChannelsCache.this.executeOp(path, operation, readOnly);
    }

    @Override
    public <T> T executeIdempotentOp(@NotNull Path path,
                                     @NotNull FileChannelIdempotentOperation<T> operation) throws IOException {
      return OpenChannelsCache.this.executeIdempotentOp(path, operation, readOnly);
    }

    @Override
    public void closeChannel(@NotNull Path path) throws IOException {
      OpenChannelsCache.this.closeChannel(path, readOnly);
    }

    @Override
    public @Nullable String describeCachedChannelOrNull(@NotNull Path path) {
      synchronized (cacheLock) {
        ChannelDescriptor descriptor = cachedChannels.get(new CacheKey(path, readOnly));
        return descriptor == null ? null : descriptor.toString();
      }
    }

    @Override
    public String toString() {
      return "OpenChannelsCache[" + cacheName + "].AccessorView[readOnly: " + readOnly + ']';
    }
  }

  private static final class PerModeStatistics {
    private int hitCount;
    private int missCount;
    private int loadCount;
  }

  private static final class CacheKey {
    private final @NotNull Path path;
    private final boolean readOnly;

    private CacheKey(@NotNull Path path,
                     boolean readOnly) {
      this.path = path;
      this.readOnly = readOnly;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof CacheKey)) {
        return false;
      }
      CacheKey key = (CacheKey)obj;
      return readOnly == key.readOnly && path.equals(key.path);
    }

    @Override
    public int hashCode() {
      return path.hashCode() * 31 + (readOnly ? 1 : 0);
    }
  }
}
