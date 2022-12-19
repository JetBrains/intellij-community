// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ShutDownTracker;
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
import java.util.concurrent.atomic.AtomicInteger;

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

  static <E extends Exception>  ByteBuffer allocate(ThrowableComputable<? extends ByteBuffer, E> computable) throws E {
    if (ourAllocator != null) {
      // Fixes IDEA-222358 Linux native memory leak
      try {
        return ourAllocator.submit(computable::compute).get();
      }
      catch (InterruptedException e) {
        Logger.getInstance(DirectByteBufferAllocator.class).error(e);
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

  private final ConcurrentSkipListMap<Integer, ArrayBlockingQueue<ByteBuffer>> myPool = new ConcurrentSkipListMap<>();
  private final AtomicInteger mySize = new AtomicInteger();
  private final int mySizeLimitInBytes;

  private static final boolean dumpStats = false;
  private static volatile int hit;
  private static volatile int miss;
  private static volatile int reused;
  private static volatile int disposed;

  static {
    if (dumpStats) {
      ShutDownTracker.getInstance().registerShutdownTask(() -> {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("pooled buffer stats: hits = " + hit + ", miss = " + miss + ", reused = " + reused + ", disposed = " + disposed);
      });
    }
  }

  public static final DirectByteBufferAllocator ALLOCATOR = new DirectByteBufferAllocator(FilePageCache.ALLOCATOR_SIZE);

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
          mySize.addAndGet(-capacity);
          if (dumpStats) {
            hit++;
          }
          return cachedBuffer;
        }
        buffers = myPool.higherEntry(capacity);
      }

      if (dumpStats) {
        miss++;
      }
    }
    return allocateNewBuffer(size);
  }

  private static ByteBuffer allocateNewBuffer(int size) {
    return allocate(() -> ByteBuffer.allocateDirect(size));
  }

  public void release(@NotNull ByteBuffer buffer) {
    if (USE_POOLED_ALLOCATOR) {
      // mySize can be slightly more than limit due to race
      if (mySize.get() < mySizeLimitInBytes) {
        int capacity = buffer.capacity();
        if (myPool.computeIfAbsent(capacity, __ -> new ArrayBlockingQueue<>(40)).offer(buffer)) {
          if (dumpStats) {
            reused++;
          }
          mySize.addAndGet(capacity);
          return;
        }
      }
      if (dumpStats) {
        disposed++;
      }
    }
    ByteBufferUtil.cleanBuffer(buffer);
  }
}
