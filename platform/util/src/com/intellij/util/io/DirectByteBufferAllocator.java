// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An allocator optimized to reuse buffers with exactly the same size.
 * In VFS/Index/PersistentMap storages typically we use 8kb, 1mb and 10mb pages.
 */
@ApiStatus.Internal
public final class DirectByteBufferAllocator {
  // Fixes IDEA-222358 Linux native memory leak. Please do not replace with BoundedTaskExecutor
  private static final ExecutorService singleThreadAllocator =
    SystemInfoRt.isLinux && SystemProperties.getBooleanProperty("idea.limit.paged.storage.allocators", true)
    ? ConcurrencyUtil.newSingleThreadExecutor("DirectBufferWrapper allocation thread")
    : null;

  private static final boolean USE_POOLED_ALLOCATOR = SystemProperties.getBooleanProperty("idea.index.use.pooled.page.allocator", true);

  /**
   * Workaround for IDEA-222358 (linux native memory 'leak'): runs code dealing with DirectByteBuffer
   * so that it doesn't trigger Linux to over-allocate too many per-thread memory pools.
   */
  private static <E extends Exception> ByteBuffer allocate(ThrowableComputable<? extends ByteBuffer, E> computable) throws E {
    if (singleThreadAllocator != null) {
      // Fixes IDEA-222358 Linux native memory leak
      try {
        return singleThreadAllocator.submit(computable::compute).get();
      }
      catch (InterruptedException e) {
        //RC: The buffer is _also_ allocated by a ourAllocator -- should be collected by GC
        //    But it could create some additional pressure on a native memory pool
        Logger.getInstance(DirectByteBufferAllocator.class).error("ByteBuffer allocation in dedicated thread was interrupted", e);
        return computable.compute();
      }
      catch (ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof OutOfMemoryError) {
          throw (OutOfMemoryError)cause; // OutOfMemoryError should be propagated (handled above)
        }
        else {
          ExceptionUtil.rethrow(e);
          throw new RuntimeException(e);//unreachable, but javac doesn't know :(
        }
      }
    }
    else {
      return computable.compute();
    }
  }


  /** How many buffers of given size to pool */
  private static final int POOL_CAPACITY_PER_BUFFER_SIZE = 40;


  /** (bufferSize -> Queue of cached buffers with such size) */
  private final ConcurrentSkipListMap<Integer, ArrayBlockingQueue<ByteBuffer>> buffersPool = new ConcurrentSkipListMap<>();

  /** Total size (bytes) of buffers that are now cached (i.e. now in cache, not in use) by the allocator */
  private final AtomicLong totalSizeOfBuffersInCache = new AtomicLong();
  /**
   * How many buffers (in bytes) allocator could keep cached in {@link #buffersPool}, instead of
   * releasing immediately
   */
  private final int maxBuffersToCacheInBytes;

  /**
   * Total size (bytes) of buffers allocated (and not released) via the allocator.
   * Buffers currently in a pool are counted as 'allocated' (i.e. not yet released)
   */
  private final AtomicLong totalSizeOfBuffersAllocated = new AtomicLong();

  //====== Statistics:

  //RC: stats fields are updated non-atomically: it is intentional, we're ready to miss a few updates

  /** Count of buffers requests served from already pooled buffer (myPool) */
  private final AtomicInteger hits = new AtomicInteger();
  /** Count of buffers requests served by allocating new buffer */
  private final AtomicInteger misses = new AtomicInteger();
  /** Count of buffers released by returning them to the pool */
  private final AtomicInteger reclaimed = new AtomicInteger();
  /** Count of buffers released by releasing them to JVM (because too many such buffers are already pooled) */
  private final AtomicInteger disposed = new AtomicInteger();

  public static final DirectByteBufferAllocator ALLOCATOR = new DirectByteBufferAllocator(
    USE_POOLED_ALLOCATOR ? PageCacheUtils.MAX_DIRECT_BUFFERS_POOL_BYTES : 0
  );

  /**
   * maxBuffersToCacheInBytes=0 disables buffer caching -- i.e. all buffers are allocated from JVM and released
   * back to JVM, without an attempt to cache them in between.
   */
  private DirectByteBufferAllocator(int maxBuffersToCacheInBytes) {
    if (maxBuffersToCacheInBytes < 0) {
      throw new IllegalArgumentException("sizeLimitInBytes(=" + maxBuffersToCacheInBytes + ") must be >=0");
    }
    this.maxBuffersToCacheInBytes = maxBuffersToCacheInBytes;
  }

  public @NotNull ByteBuffer allocate(int size) {
    if (useBuffersCache()) {
      Map.Entry<Integer, ArrayBlockingQueue<ByteBuffer>> buffersOfCapacity = buffersPool.ceilingEntry(size);

      while (buffersOfCapacity != null) {

        int capacity = buffersOfCapacity.getKey();
        if (capacity > size * 2) {
          //It's ok to use buffer(capacity: 32k) to serve request of size=16k, but we don't want to
          // use 10M buffer to serve request of size=16k because it is too much waste.
          // By limiting the capacity <=2*size we limit the amount of memory wasted due to 'rounding'.
          // If we can't serve the request from pool wasting no more than twice the requested memory
          // -- fallback to allocating the buffer from JVM (and hope JVM/stdlib/OS allocators are
          // smart enough to not waste even more memory than we could have been wasted :).

          break;
        }

        ByteBuffer cachedBuffer = buffersOfCapacity.getValue().poll();
        if (cachedBuffer != null) {
          cachedBuffer.clear().limit(size);
          totalSizeOfBuffersInCache.addAndGet(-capacity);
          hits.incrementAndGet();
          return cachedBuffer;
        }

        //Don't remove the entry even though it is empty -- because it creates race condition
        // with adding the same entry again, and some buffers may be lost due to that race.

        buffersOfCapacity = buffersPool.higherEntry(capacity);
      }
    }

    misses.incrementAndGet();
    totalSizeOfBuffersAllocated.addAndGet(size);
    return allocateNewBuffer(size);
  }

  private boolean useBuffersCache() {
    return maxBuffersToCacheInBytes > 0;
  }

  public void release(@NotNull ByteBuffer buffer) {
    if (useBuffersCache()) {
      // We allow totalSizeOfBuffersInCache to become slightly more than the limit due to race. It is
      // a tradeoff: we have >1 condition to satisfy (totalSizeOfBuffersInCache and POOL_CAPACITY_PER_BUFFER_SIZE),
      // and satisfying them both atomically requires too much effort -- better give a slack to one of them.
      if (totalSizeOfBuffersInCache.get() < maxBuffersToCacheInBytes) {
        int capacity = buffer.capacity();
        if (buffersPool.computeIfAbsent(capacity, __ -> new ArrayBlockingQueue<>(POOL_CAPACITY_PER_BUFFER_SIZE)).offer(buffer)) {
          totalSizeOfBuffersInCache.addAndGet(capacity);
          reclaimed.incrementAndGet();
          return;
        }
      }
    }

    releaseBufferWithoutCaching(buffer);
  }

  private void releaseBufferWithoutCaching(@NotNull ByteBuffer buffer) {
    totalSizeOfBuffersAllocated.addAndGet(-buffer.capacity());
    ByteBufferUtil.cleanBuffer(buffer);
    disposed.incrementAndGet();
  }

  private static ByteBuffer allocateNewBuffer(int size) {
    return allocate(() -> ByteBuffer.allocateDirect(size));
  }

  public void releaseCachedBuffers() {
    for (ArrayBlockingQueue<ByteBuffer> queue : buffersPool.values()) {
      ByteBuffer buffer;
      while ((buffer = queue.poll()) != null) {
        releaseBufferWithoutCaching(buffer);
      }
    }
  }

  public Statistics getStatistics() {
    return new Statistics(
      hits.get(), misses.get(),
      reclaimed.get(), disposed.get(),
      totalSizeOfBuffersInCache.get(),
      totalSizeOfBuffersAllocated.get()
    );
  }

  public static final class Statistics {
    /** Buffers requests served from already pooled buffer (myPool) */
    public final int hits;
    /** Buffers requests served by allocating new buffer */
    public final int misses;
    /** Buffers released by returning them to the pool */
    public final int reclaimed;
    /** Buffers released by releasing them to JVM (because too many such buffers are already pooled) */
    public final int disposed;
    /** Total size of all buffers in the Allocator cache at the moment (bytes) */
    public final long totalSizeOfBuffersCachedInBytes;
    /**
     * Total size of all buffers allocated (and not yet released) via the Allocator.
     * Buffers currently in a pool are counted as 'allocated' (i.e. not yet released)
     */
    public final long totalSizeOfBuffersAllocatedInBytes;

    private Statistics(int hits,
                       int misses,
                       int reclaimed,
                       int disposed,
                       long totalSizeOfBuffersCachedInBytes,
                       long totalSizeOfBuffersAllocatedInBytes) {
      this.hits = hits;
      this.misses = misses;
      this.reclaimed = reclaimed;
      this.disposed = disposed;
      this.totalSizeOfBuffersCachedInBytes = totalSizeOfBuffersCachedInBytes;
      this.totalSizeOfBuffersAllocatedInBytes = totalSizeOfBuffersAllocatedInBytes;
    }
  }
}
