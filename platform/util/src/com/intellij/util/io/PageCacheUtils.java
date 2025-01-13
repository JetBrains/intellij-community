// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.MathUtil;
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

  //@formatter:off

  /** 10Mb default, or SystemProperty('idea.max.paged.storage.cache')Mb, but not less than 1 Mb */
  public static final int DEFAULT_PAGE_SIZE = Math.max(1, getIntProperty("idea.paged.storage.page.size", 10)) * MiB;

  /**
   * How much direct (native) memory is available to use, in total.
   * Basically, we extract direct memory available in JVM, and cut off a bit for a safety margin.
   */
  public static final long MAX_DIRECT_MEMORY_TO_USE_BYTES = maxDirectMemory() - 2L * DEFAULT_PAGE_SIZE;

  /**
   * Total size of the cache(s), bytes -- usually ~600 Mb.
   * We start with default size 600Mb (200Mb on 32bit platforms), and try to fit that into
   * [min: 100Mb, max: direct memory available in JVM).
   */
  public static final long FILE_PAGE_CACHES_TOTAL_CAPACITY_BYTES = MathUtil.clamp(
    getLongProperty("file-page-cache.cache-capacity-mb", CpuArch.is32Bit() ? 200 : 600) * MiB,
    Math.min(100 * MiB, MAX_DIRECT_MEMORY_TO_USE_BYTES),
    MAX_DIRECT_MEMORY_TO_USE_BYTES
  );

  /**
   * Enables {@link FilePageCacheLockFree new file cache} to be used by various VFS/Indexes storages.
   * So far both new and {@link FilePageCache legacy} file caches co-exist: storages are incrementally migrated
   * to new cache
   */
  public static final boolean LOCK_FREE_PAGE_CACHE_ENABLED = getBooleanProperty("vfs.lock-free-impl.enable", false);

  /**
   * How much direct memory the new (code name 'lock-free') FilePageCache impl allowed to utilize: as a fraction
   * of total direct memory reserved for file caching {@link #FILE_PAGE_CACHES_TOTAL_CAPACITY_BYTES}).
   * I.e., if FILE_PAGE_CACHES_TOTAL_CAPACITY_BYTES=100Mb, and NEW_PAGE_CACHE_MEMORY_FRACTION=0.1
   * => 10Mb is allocated for the new cache, and 90Mb for the old one.
   */
  public static final double NEW_PAGE_CACHE_MEMORY_FRACTION = getFloatProperty("vfs.lock-free-impl.fraction-direct-memory-to-utilize", 0.2f);


  public static final long FILE_PAGE_CACHE_NEW_CAPACITY_BYTES = LOCK_FREE_PAGE_CACHE_ENABLED ?
                                                                Math.round(NEW_PAGE_CACHE_MEMORY_FRACTION * FILE_PAGE_CACHES_TOTAL_CAPACITY_BYTES) :
                                                                0;

  public static final long FILE_PAGE_CACHE_OLD_CAPACITY_BYTES = FILE_PAGE_CACHES_TOTAL_CAPACITY_BYTES - FILE_PAGE_CACHE_NEW_CAPACITY_BYTES;

  /** Capacity of direct byte buffers _pool_ inside {@linkplain DirectByteBufferAllocator} */
  static final int MAX_DIRECT_BUFFERS_POOL_BYTES = (int)Math.min(
    100 * MiB,
    Math.max(0, MAX_DIRECT_MEMORY_TO_USE_BYTES - FILE_PAGE_CACHES_TOTAL_CAPACITY_BYTES - 300 * MiB)
  );

  /**
   * How much memory FilePageCache could allocate above FILE_PAGE_CACHE_NEW_CAPACITY_BYTES, from heap -- temporary,
   * to serve page request(s) without delay -- at the cost of more GC pressure later.
   * Fraction of 'hard' capacity {@link #FILE_PAGE_CACHE_NEW_CAPACITY_BYTES}
   */
  static final float HEAP_CAPACITY_FRACTION = getFloatProperty("vfs.lock-free-impl.heap-capacity-ratio", 0.1f);


  private static final int CHANNELS_CACHE_CAPACITY = getIntProperty("paged.file.storage.open.channel.cache.capacity", 400);

  //@formatter:on

  /** Shared channels cache */
  static final OpenChannelsCache CHANNELS_CACHE = new OpenChannelsCache(CHANNELS_CACHE_CAPACITY);

  static {
    LOG.info(
      "File page caching params:\n" +
      "\tDEFAULT_PAGE_SIZE: " + DEFAULT_PAGE_SIZE + "\n" +
      "\tDirect memory to use, max: " + MAX_DIRECT_MEMORY_TO_USE_BYTES + "\n" +
      (LOCK_FREE_PAGE_CACHE_ENABLED
       ? "\tFilePageCache: regular + lock-free (LOCK_FREE_PAGE_CACHE_ENABLED:true)\n" +
         "\tNEW_PAGE_CACHE_MEMORY_FRACTION: " + NEW_PAGE_CACHE_MEMORY_FRACTION + "\n" +
         "\tRegular FilePageCache: " + FILE_PAGE_CACHE_OLD_CAPACITY_BYTES + " bytes\n" +
         "\tNew     FilePageCache: " + FILE_PAGE_CACHE_NEW_CAPACITY_BYTES + " bytes" +
         " (+ up to " + (HEAP_CAPACITY_FRACTION * 100) + "% overflow)\n"
       : "\tFilePageCache: regular\n" +
         "\tRegular FilePageCache: " + FILE_PAGE_CACHE_OLD_CAPACITY_BYTES + " bytes\n") +
      "\tDirectByteBuffers pool: " + MAX_DIRECT_BUFFERS_POOL_BYTES + " bytes"
    );
  }

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
      //noinspection JavaReflectionMemberAccess
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

  private PageCacheUtils() { throw new AssertionError("Not for instantiation"); }
}
