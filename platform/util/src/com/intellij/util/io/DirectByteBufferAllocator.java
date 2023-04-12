// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An allocator optimized to reuse buffers with exactly the same size.
 * In VFS/Index/PersistentMap storages typically we use 8kb, 1mb and 10mb pages.
 */
@ApiStatus.Internal
public final class DirectByteBufferAllocator {
  // Fixes IDEA-222358 Linux native memory leak. Please do not replace to BoundedTaskExecutor
  private static final ExecutorService ourAllocator =
    SystemInfoRt.isLinux && Boolean.parseBoolean(System.getProperty("idea.limit.paged.storage.allocators", "true"))
    ? ConcurrencyUtil.newSingleThreadExecutor("DirectBufferWrapper allocation thread")
    : null;

  private static final boolean USE_POOLED_ALLOCATOR = SystemProperties.getBooleanProperty("idea.index.use.pooled.page.allocator", true);

  /**
   * Workaround for IDEA-222358 (linux native memory 'leak'): runs code dealing with DirectByteBuffer
   * so that it doesn't trigger Linux to over-allocate too many per-thread memory pools.
   */
  static <E extends Exception> ByteBuffer allocate(ThrowableComputable<? extends ByteBuffer, E> computable) throws E {
    if (ourAllocator != null) {
      // Fixes IDEA-222358 Linux native memory leak
      try {
        return ourAllocator.submit(computable::compute).get();
      }
      catch (InterruptedException e) {
        Logger.getInstance(DirectByteBufferAllocator.class).error("ByteBuffer allocation in dedicated thread was interrupted", e);
        return computable.compute();
      }
      catch (ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof OutOfMemoryError) {
          throw (OutOfMemoryError)cause; // OutOfMemoryError should be propagated (handled above)
        }
        else {
          throw new RuntimeException(e);
        }
      }
    }
    else {
      return computable.compute();
    }
  }


  /** How many buffers of given size to pool */
  private static final int POOL_CAPACITY_PER_BUFFER_SIZE = 40;


  private final ConcurrentSkipListMap<Integer, ArrayBlockingQueue<ByteBuffer>> myPool = new ConcurrentSkipListMap<>();

  /** Total size (bytes) of buffers that are now cached (i.e. now in cache, not in use) by the allocator */
  private final AtomicLong totalSizeOfBuffersInCache = new AtomicLong();
  private final int mySizeLimitInBytes;

  /** Total size (bytes) of buffers allocated (and not released) via the allocator */
  private final AtomicLong totalSizeOfBuffersAllocated = new AtomicLong();

  //====== Statistics:

  //RC: stats fields are updated non-atomically: it is intentional, we're ready to miss a few updates

  /** Count of buffers requests served from already pooled buffer (myPool) */
  private volatile int hits;
  /** Count of buffers requests served by allocating new buffer */
  private volatile int misses;
  /** Count of buffers released by returning them to the pool */
  private volatile int reclaimed;
  /** Count of buffers released by releasing them to JVM (because too many such buffers are already pooled) */
  private volatile int disposed;

  public static final DirectByteBufferAllocator ALLOCATOR = new DirectByteBufferAllocator(PageCacheUtils.ALLOCATOR_SIZE);

  private DirectByteBufferAllocator(int sizeLimitInBytes) {
    mySizeLimitInBytes = sizeLimitInBytes;
  }

  public @NotNull ByteBuffer allocate(final int size) {
    if (USE_POOLED_ALLOCATOR) {
      Map.Entry<Integer, ArrayBlockingQueue<ByteBuffer>> buffers = myPool.ceilingEntry(size);

      while (buffers != null) {
        ByteBuffer cachedBuffer = buffers.getValue().poll();
        int capacity = buffers.getKey();

        if (cachedBuffer != null) {
          cachedBuffer.rewind();
          cachedBuffer.limit(size);
          totalSizeOfBuffersInCache.addAndGet(-capacity);
          //noinspection NonAtomicOperationOnVolatileField
          hits++;
          return cachedBuffer;
        }
        buffers = myPool.higherEntry(capacity);
      }
    }
    //noinspection NonAtomicOperationOnVolatileField
    misses++;
    totalSizeOfBuffersAllocated.addAndGet(size);
    return allocateNewBuffer(size);
  }

  private static ByteBuffer allocateNewBuffer(int size) {
    return allocate(() -> ByteBuffer.allocateDirect(size));
  }

  public void release(@NotNull ByteBuffer buffer) {
    int capacity = buffer.capacity();
    if (USE_POOLED_ALLOCATOR) {
      // mySize can be slightly more than limit due to race
      if (totalSizeOfBuffersInCache.get() < mySizeLimitInBytes) {
        if (myPool.computeIfAbsent(capacity, __ -> new ArrayBlockingQueue<>(POOL_CAPACITY_PER_BUFFER_SIZE)).offer(buffer)) {
          totalSizeOfBuffersInCache.addAndGet(capacity);
          //noinspection NonAtomicOperationOnVolatileField
          reclaimed++;
          return;
        }
      }
    }
    totalSizeOfBuffersAllocated.addAndGet(-capacity);
    ByteBufferUtil.cleanBuffer(buffer);
    //noinspection NonAtomicOperationOnVolatileField
    disposed++;
  }

  public Statistics getStatistics() {
    return new Statistics(
      hits, misses, reclaimed, disposed,
      totalSizeOfBuffersInCache.get(),
      totalSizeOfBuffersAllocated.get()
    );
  }

  public static class Statistics {
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
    /** Total size of all buffers allocated (and not yet released) via the Allocator */
    public final long totalSizeOfBuffersAllocatedInBytes;

    private Statistics(final int hits,
                       final int misses,
                       final int reclaimed,
                       final int disposed,
                       final long totalSizeOfBuffersCachedInBytes,
                       final long totalSizeOfBuffersAllocatedInBytes) {
      this.hits = hits;
      this.misses = misses;
      this.reclaimed = reclaimed;
      this.disposed = disposed;
      this.totalSizeOfBuffersCachedInBytes = totalSizeOfBuffersCachedInBytes;
      this.totalSizeOfBuffersAllocatedInBytes = totalSizeOfBuffersAllocatedInBytes;
    }
  }
}
