// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static com.intellij.util.SystemProperties.*;
import static com.intellij.util.io.IOUtil.MiB;

/**
 * Constants, params, static functions around file page caching
 */
@ApiStatus.Internal
public final class PageCacheUtils {
  private static final Logger LOG = Logger.getInstance(PageCacheUtils.class);

  /**
   * 10Mb default, or SystemProperty('idea.max.paged.storage.cache')Mb, but not less than 1 Mb
   */
  public static final int DEFAULT_PAGE_SIZE = Math.max(1, getIntProperty("idea.paged.storage.page.size", 10)) * MiB;

  /**
   * Enables new (code-name 'lock-free') implementations for various VFS components.
   * So far they co-exist with the legacy implementations
   */
  public static final boolean LOCK_FREE_VFS_ENABLED = getBooleanProperty("vfs.lock-free-impl.enable", false);

  /**
   * How much direct memory new (code name 'lock-free') FilePageCache impl allowed to utilize:
   * a fraction of total direct memory. I.e. if MAX_DIRECT_MEMORY_TO_USE_BYTES=100Mb, and
   * NEW_PAGE_CACHE_MEMORY_FRACTION=0.1 => 10Mb will be allocated for new cache, and 90Mb for
   * the old one.
   */
  public static final double NEW_PAGE_CACHE_MEMORY_FRACTION =
    getFloatProperty("vfs.lock-free-impl.fraction-direct-memory-to-utilize", 0.1f);

  /**
   * How much direct memory we're ready to use for file page cache(s) -- in total.
   * Basically, we extract direct memory available in JVM, and subtract a bit -- to leave space
   * for others (we're not the only ones who use direct memory in JVM)
   */
  public static final long MAX_DIRECT_MEMORY_TO_USE_BYTES = maxDirectMemory() - 2L * DEFAULT_PAGE_SIZE;


  /**
   * Total size of the cache(s), bytes -- somewhere ~100-500 Mb, see static initializer for exact logic.
   */
  public static final long FILE_PAGE_CACHES_TOTAL_CAPACITY_BYTES = estimateTotalCacheCapacityLimit(MAX_DIRECT_MEMORY_TO_USE_BYTES);


  /**
   * @return estimation of direct memory available in current JVM, in bytes.
   * Controlled by -XX:MaxDirectMemorySize=<size>
   */
  private static long maxDirectMemory() {
    try {
      Class<?> aClass = Class.forName("jdk.internal.misc.VM");
      Method maxDirectMemory = aClass.getMethod("maxDirectMemory");
      return (Long)maxDirectMemory.invoke(null);
    }
    catch (Throwable ignore) {
    }

    try {
      Class<?> aClass = Class.forName("sun.misc.VM");
      Method maxDirectMemory = aClass.getMethod("maxDirectMemory");
      return (Long)maxDirectMemory.invoke(null);
    }
    catch (Throwable ignore) {
    }

    try {
      Class<?> aClass = Class.forName("java.nio.Bits");
      Field maxMemory = aClass.getDeclaredField("maxMemory");
      maxMemory.setAccessible(true);
      return (Long)maxMemory.get(null);
    }
    catch (Throwable ignore) {
    }

    try {
      Class<?> aClass = Class.forName("java.nio.Bits");
      Field maxMemory = aClass.getDeclaredField("MAX_MEMORY");
      maxMemory.setAccessible(true);
      return (Long)maxMemory.get(null);
    }
    catch (Throwable ignore) {
    }

    return Runtime.getRuntime().maxMemory();
  }

  private static long estimateTotalCacheCapacityLimit(final long maxDirectMemoryToUseBytes) {
    //RC: Basically, try to allocate cache of sys("idea.max.paged.storage.cache"),
    //    with default 500Mb on 64bit, and 200Mb on 32bit platforms,
    //    but not less than 100Mb, and not more than maxDirectMemoryToUseBytes (strictly)
    //
    //    Now, usually, maxDirectMemoryToUseBytes=2Gb, so if one does not overwrite
    //    'idea.max.paged.storage.cache' then everything below simplified down to 'just 500Mb'
    //    ('cos 32bit platforms are infrequent today)


    final int defaultCacheCapacityMb = CpuArch.is32Bit() ? 200 : 500;

    final int cacheCapacityMb = getIntProperty("idea.max.paged.storage.cache", defaultCacheCapacityMb);
    final long cacheCapacityBytes = cacheCapacityMb * (long)MiB;

    final int minCacheCapacityMb = 100;
    final long minCacheCapacityBytes = Math.min(minCacheCapacityMb * MiB, maxDirectMemoryToUseBytes);

    return Math.min(
      Math.max(minCacheCapacityBytes, cacheCapacityBytes),
      maxDirectMemoryToUseBytes
    );
  }

  public static final long FILE_PAGE_CACHE_NEW_CAPACITY_BYTES =
    LOCK_FREE_VFS_ENABLED ?
    (long)(NEW_PAGE_CACHE_MEMORY_FRACTION * FILE_PAGE_CACHES_TOTAL_CAPACITY_BYTES) :
    0;

  public static final long FILE_PAGE_CACHE_OLD_CAPACITY_BYTES =
    LOCK_FREE_VFS_ENABLED ?
    (long)((1 - NEW_PAGE_CACHE_MEMORY_FRACTION) * FILE_PAGE_CACHES_TOTAL_CAPACITY_BYTES) :
    FILE_PAGE_CACHES_TOTAL_CAPACITY_BYTES;

  /**
   * Capacity of {@linkplain DirectByteBufferAllocator}
   */
  static final int ALLOCATOR_SIZE = (int)Math.min(
    100 * MiB,
    Math.max(0, MAX_DIRECT_MEMORY_TO_USE_BYTES - FILE_PAGE_CACHES_TOTAL_CAPACITY_BYTES - 300 * MiB)
  );


  private static final int CHANNELS_CACHE_CAPACITY = getIntProperty("paged.file.storage.open.channel.cache.capacity", 400);

  /** Shared channels cache */
  static final OpenChannelsCache CHANNELS_CACHE = new OpenChannelsCache(CHANNELS_CACHE_CAPACITY);

  static {
    LOG.info("File page caching params:");
    LOG.info("\tDEFAULT_PAGE_SIZE:" + DEFAULT_PAGE_SIZE);
    if (LOCK_FREE_VFS_ENABLED) {
      LOG.info("\tFilePageCache: regular + lock-free (LOCK_FREE_VFS_ENABLED:true)");
      LOG.info("\tNEW_PAGE_CACHE_MEMORY_FRACTION: " + NEW_PAGE_CACHE_MEMORY_FRACTION);
      LOG.info("\tRegular FilePageCache: " + FILE_PAGE_CACHE_OLD_CAPACITY_BYTES + " bytes");
      LOG.info("\tNew     FilePageCache: " + FILE_PAGE_CACHE_NEW_CAPACITY_BYTES + " bytes");
    }
    else {
      LOG.info("\tFilePageCache: regular");
    }

    LOG.info("\tDirectByteBuffers pool: " + ALLOCATOR_SIZE + " bytes");
  }

  private PageCacheUtils() { throw new AssertionError("Not for instantiation"); }
}
