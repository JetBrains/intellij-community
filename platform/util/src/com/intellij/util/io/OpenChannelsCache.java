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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Cache of opened {@link FileChannel}s.
 * Cache eviction policy is kind of LRU -- the oldest channel accessed is the first to be evicted, given it is not used right now.
 * <p>
 * The cache exposes two mode-bound {@link ChannelsAccessor} views: {@link #asReadOnly()} and {@link #asWritable()}.
 * BEWARE: cache caches (potentially) 2 different {@linkplain FileChannel} instances: readOnly and !readOnly.
 * Generally, it is not guaranteed these 2 different FileChannels instances always share the same data -- they
 * could, but also there could be some temporary difference in the content visible via readOnly and !readOnly
 * FileChannel. So, better avoid accessing the same path via 2 different readOnly/!readOnly FileChannels: use
 * the single accessor for _all_ the accesses to the given Path.
 */
@ApiStatus.Internal
public final class OpenChannelsCache {
  /** for {@linkplain #toString()} */
  private final String cacheName;

  /** Max channels to keep open in cache */
  private final int capacity;

  //@GuardedBy("cacheLock")
  private final @NotNull Map<CacheKey, ChannelDescriptor> cachedChannels;
  //@GuardedBy("cacheLock")
  private final @NotNull Map<CacheKey, Thread> openingChannels = new HashMap<>();
  //@GuardedBy("cacheLock")
  private final @NotNull Map<CacheKey, Thread> closingChannels = new HashMap<>();

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
    CacheKey key = new CacheKey(path, readOnly);
    boolean descriptorsWereDropped = false;
    boolean cacheWasOverCapacity = false;
    while (true) {
      List<DetachedChannelDescriptor> descriptorsToClose;
      synchronized (cacheLock) {
        waitForPendingOpen(key);
        waitForPendingClose(key);

        ChannelDescriptor descriptor = cachedChannels.get(key);
        if (descriptor != null) {
          PerModeStatistics statistics = statisticsFor(readOnly);
          statistics.hitCount++;
          descriptor.lock();
          return descriptor;
        }

        EvictionResult eviction = detachOverCachedChannels(1);
        descriptorsToClose = eviction.descriptorsToClose;
        cacheWasOverCapacity |= eviction.cacheWasOverCapacity;
        if (descriptorsToClose.isEmpty()) {
          openingChannels.put(key, Thread.currentThread());
          break;
        }

        descriptorsWereDropped = true;
      }

      closeDetachedChannels(descriptorsToClose);
    }

    ChannelDescriptor descriptor = null;
    try {
      descriptor = new ChannelDescriptor(path, readOnly, channelOpener);

      while (true) {
        List<DetachedChannelDescriptor> descriptorsToClose;
        synchronized (cacheLock) {
          EvictionResult eviction = detachOverCachedChannels(1);
          descriptorsToClose = eviction.descriptorsToClose;
          cacheWasOverCapacity |= eviction.cacheWasOverCapacity;
          if (descriptorsToClose.isEmpty()) {
            cachedChannels.put(key, descriptor);
            PerModeStatistics statistics = statisticsFor(readOnly);
            if (descriptorsWereDropped || cacheWasOverCapacity) {
              statistics.missCount++;
            }
            else {
              statistics.loadCount++;
            }
            descriptor.lock();
            finishOpeningUnderLock(key);
            return descriptor;
          }

          descriptorsWereDropped = true;
        }

        closeDetachedChannels(descriptorsToClose);
      }
    }
    catch (Throwable t) {
      finishOpening(key);
      if (descriptor != null) {
        try {
          descriptor.close();
        }
        catch (Throwable closeError) {
          t.addSuppressed(closeError);
        }
      }
      throwAsIOExceptionOrUnchecked(t);
      throw new AssertionError("unreachable");
    }
  }

  // If there is a channel for a key pending to open -- waits for it to be actually opened.
  // Must be called under cacheLock (but releases the lock while waiting).
  private void waitForPendingOpen(@NotNull CacheKey key) {
    waitForPendingOperation(key, openingChannels);
  }

  // If there is a channel for a key pending to close -- waits for it to be actually closed.
  // Must be called under cacheLock (but releases the lock while waiting).
  private void waitForPendingClose(@NotNull CacheKey key) {
    waitForPendingOperation(key, closingChannels);
  }

  private void waitForPendingOperation(@NotNull CacheKey key,
                                       @NotNull Map<CacheKey, Thread> pendingOperationThreads) {
    boolean interrupted = false;
    while (true) {
      Thread thread = pendingOperationThreads.get(key);
      if (thread == null || thread == Thread.currentThread()) {
        break;
      }
      try {
        cacheLock.wait();
      }
      catch (InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private @NotNull PerModeStatistics statisticsFor(boolean readOnly) {
    return readOnly ? readOnlyStats : writableStats;
  }

  private void releaseDescriptor(@NotNull ChannelDescriptor descriptor) {
    synchronized (cacheLock) {
      descriptor.unlock();
    }
  }

  private void closeChannel(@NotNull Path path,
                            boolean readOnly) throws IOException {
    DetachedChannelDescriptor descriptorToClose;
    synchronized (cacheLock) {
      CacheKey key = new CacheKey(path, readOnly);
      waitForPendingOpen(key);
      waitForPendingClose(key);
      descriptorToClose = detachChannel(key);
    }

    if (descriptorToClose != null) {
      closeDetachedChannel(descriptorToClose);
    }
  }

  /// @param slotsToFree after this method it should be at least slotsToFree slots available until capacity
  //@GuardedBy(cacheLock)
  @SuppressWarnings("SameParameterValue")
  private @NotNull EvictionResult detachOverCachedChannels(int slotsToFree) {
    int channelsToEvict = cachedChannels.size() - capacity + slotsToFree;

    if (channelsToEvict <= 0) {
      return EvictionResult.NOT_NEEDED;
    }

    List<CacheKey> keysToEvict = new ArrayList<>();
    for (Map.Entry<CacheKey, ChannelDescriptor> entry : cachedChannels.entrySet()) {
      if (channelsToEvict <= 0) break;
      ChannelDescriptor channelDescriptor = entry.getValue();
      if (!channelDescriptor.isLocked()) {
        keysToEvict.add(entry.getKey());
        channelsToEvict--;
      }
    }

    List<DetachedChannelDescriptor> descriptorsToClose = new ArrayList<>(keysToEvict.size());
    for (CacheKey keyToDrop : keysToEvict) {
      DetachedChannelDescriptor descriptorToClose = detachChannel(keyToDrop);
      if (descriptorToClose != null) {
        descriptorsToClose.add(descriptorToClose);
      }
    }

    return new EvictionResult(/* wasOverCapacity: */ true, descriptorsToClose);
  }

  //@GuardedBy(cacheLock)
  private @Nullable DetachedChannelDescriptor detachChannel(@NotNull CacheKey key) {
    ChannelDescriptor descriptor = cachedChannels.remove(key);

    if (descriptor != null) {
      assert !descriptor.isLocked() : "Channel is in use: " + descriptor;
      closingChannels.put(key, Thread.currentThread());
      return new DetachedChannelDescriptor(key, descriptor);
    }

    return null;
  }

  private void closeDetachedChannels(@NotNull List<DetachedChannelDescriptor> descriptorsToClose) throws IOException {
    Throwable error = null;
    for (DetachedChannelDescriptor descriptorToClose : descriptorsToClose) {
      try {
        closeDetachedChannel(descriptorToClose);
      }
      catch (Throwable t) {
        if (error == null) {
          error = t;
        }
        else {
          error.addSuppressed(t);
        }
      }
    }

    if (error == null) {
      return;
    }
    throwAsIOExceptionOrUnchecked(error);
  }

  private static void throwAsIOExceptionOrUnchecked(@NotNull Throwable error) throws IOException {
    if (error instanceof IOException) {
      throw (IOException)error;
    }
    if (error instanceof RuntimeException) {
      throw (RuntimeException)error;
    }
    if (error instanceof Error) {
      throw (Error)error;
    }
    throw new IOException(error);
  }

  private void closeDetachedChannel(@NotNull DetachedChannelDescriptor descriptorToClose) throws IOException {
    try {
      descriptorToClose.descriptor.close();
    }
    finally {
      finishClosing(descriptorToClose.key);
    }
  }

  private void finishClosing(@NotNull CacheKey key) {
    synchronized (cacheLock) {
      closingChannels.remove(key);
      cacheLock.notifyAll();
    }
  }

  private void finishOpening(@NotNull CacheKey key) {
    synchronized (cacheLock) {
      finishOpeningUnderLock(key);
    }
  }

  //@GuardedBy(cacheLock)
  private void finishOpeningUnderLock(@NotNull CacheKey key) {
    openingChannels.remove(key);
    cacheLock.notifyAll();
  }

  static final class ChannelDescriptor implements Closeable {
    private final @NotNull FileChannel channel;
    private final boolean readOnly;

    private int lockCount = 0;

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

  private static final class EvictionResult {
    public static final EvictionResult NOT_NEEDED = new EvictionResult(/* overCapacity: */ false, Collections.emptyList());

    private final boolean cacheWasOverCapacity;
    private final @NotNull List<DetachedChannelDescriptor> descriptorsToClose;

    private EvictionResult(boolean cacheWasOverCapacity,
                           @NotNull List<DetachedChannelDescriptor> descriptorsToClose) {
      this.cacheWasOverCapacity = cacheWasOverCapacity;
      this.descriptorsToClose = descriptorsToClose;
    }
  }

  private static final class DetachedChannelDescriptor {
    private final @NotNull CacheKey key;
    private final @NotNull ChannelDescriptor descriptor;

    private DetachedChannelDescriptor(@NotNull CacheKey key,
                                      @NotNull ChannelDescriptor descriptor) {
      this.key = key;
      this.descriptor = descriptor;
    }
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
