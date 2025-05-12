// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.namecache;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.util.IntSLRUCache;
import com.intellij.util.containers.IntObjectLRUMap;
import com.intellij.util.io.DataEnumeratorEx;
import com.intellij.util.io.URLUtil;
import io.opentelemetry.api.metrics.Meter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.platform.diagnostic.telemetry.PlatformScopesKt.VFS;
import static com.intellij.util.SystemProperties.getBooleanProperty;

@ApiStatus.Internal
public final class SLRUFileNameCache implements FileNameCache {
  private static final boolean TRACK_STATS = getBooleanProperty("vfs.name-cache.track-stats", true);

  //TODO RC: cache size(s) better be ctor parameter(s)
  private static final int PROTECTED_SEGMENTS_TOTAL_SIZE = 40000;
  private static final int PROBATION_SEGMENTS_TOTAL_SIZE = 20000;
  private static final int MRU_CACHE_SIZE = 1024;

  @SuppressWarnings("unchecked")
  private final IntSLRUCache<String>[] cacheSegments = new IntSLRUCache[16];
  @SuppressWarnings("unchecked")
  private final IntObjectLRUMap.MapEntry<String>[] mruCache = new IntObjectLRUMap.MapEntry[MRU_CACHE_SIZE];

  private final @NotNull DataEnumeratorEx<String> namesEnumerator;

  private final boolean checkFileNamesSanity;

  //===================== monitoring: ===============================================================

  private final AtomicInteger requestsCount = new AtomicInteger();
  private final AtomicInteger fastCacheMissesCount = new AtomicInteger();
  private final AtomicInteger totalCacheMissesCount = new AtomicInteger();
  private final @Nullable AutoCloseable otelHandlerToClose;

  public SLRUFileNameCache(@NotNull DataEnumeratorEx<String> namesEnumerator) {
    this(namesEnumerator, isFileNameSanityCheckEnabledByDefault());
  }

  public SLRUFileNameCache(@NotNull DataEnumeratorEx<String> namesEnumerator,
                           boolean checkFileNamesSanity) {
    this.namesEnumerator = namesEnumerator;
    int protectedSize = PROTECTED_SEGMENTS_TOTAL_SIZE / cacheSegments.length;
    int probationalSize = PROBATION_SEGMENTS_TOTAL_SIZE / cacheSegments.length;
    for (int i = 0; i < cacheSegments.length; i++) {
      cacheSegments[i] = new IntSLRUCache<>(protectedSize, probationalSize);
    }
    this.checkFileNamesSanity = checkFileNamesSanity;

    otelHandlerToClose = TRACK_STATS ?
                         setupReportingToOpenTelemetry() :
                         null;
  }

  @Override
  public int tryEnumerate(@NotNull String name) throws IOException {
    //assertShortFileName(name);
    int nameId = namesEnumerator.tryEnumerate(name);
    if (nameId != NULL_ID) {
      cacheData(name, nameId, calcStripeIdFromNameId(nameId));
    }
    return nameId;
  }

  @Override
  public int enumerate(@NotNull String name) throws IOException {
    if (checkFileNamesSanity) {
      assertShortFileName(name);
    }

    int nameId = namesEnumerator.enumerate(name);
    cacheData(name, nameId, calcStripeIdFromNameId(nameId));
    return nameId;
  }

  @Override
  public @NotNull String valueOf(int nameId) throws IOException {
    assert nameId > 0 : nameId;

    if (TRACK_STATS) {
      requestsCount.incrementAndGet();
    }

    int mruCacheEntryIndex = nameId % MRU_CACHE_SIZE;
    IntObjectLRUMap.MapEntry<String> entry = mruCache[mruCacheEntryIndex];
    if (entry != null && entry.key == nameId) {
      return entry.value;
    }

    if (TRACK_STATS) {
      fastCacheMissesCount.incrementAndGet();
    }

    final int stripe = calcStripeIdFromNameId(nameId);
    IntSLRUCache<String> cache = cacheSegments[stripe];
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (cache) {
      entry = cache.getCachedEntry(nameId);
    }
    if (entry == null) {
      if (TRACK_STATS) {
        totalCacheMissesCount.incrementAndGet();
      }

      String name = namesEnumerator.valueOf(nameId);
      if (name == null) {
        throw new IOException("VFS name enumerator corrupted: nameId(=" + nameId + ") is not found in enumerator (=null)");
      }

      entry = cacheData(name, nameId, stripe);
    }
    mruCache[mruCacheEntryIndex] = entry;
    return entry.value;
  }

  @Override
  public void close() throws Exception {
    if (otelHandlerToClose != null) {
      otelHandlerToClose.close();
    }
  }

  private @NotNull IntObjectLRUMap.MapEntry<String> cacheData(@Nullable String name, int nameId, int stripe) {
    IntSLRUCache<String> cache = cacheSegments[stripe];
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (cache) {
      return cache.cacheEntry(nameId, name);
    }
  }


  private int calcStripeIdFromNameId(int id) {
    int h = id;
    h -= h << 6;
    h ^= h >> 17;
    h -= h << 9;
    h ^= h << 4;
    h -= h << 3;
    h ^= h << 10;
    h ^= h >> 15;
    return h % cacheSegments.length;
  }

  private static final String FS_SEPARATORS = "/" + (File.separatorChar == '/' ? "" : File.separatorChar);

  @VisibleForTesting
  public static void assertShortFileName(@NotNull String name) throws IllegalArgumentException {
    //TODO RC: those verification rules are very wierd, they seems to be just cherry-picked to solve
    //         specific problems. We should either abandon verification altogether, or formulate simple
    //         and consistent rules.
    if (name.length() <= 1) return;
    int start = 0;
    int end = name.length();
    if (SystemInfo.isWindows && name.startsWith("//")) {
      // Windows UNC: '//Network/Ubuntu'
      // We allow paths <=2 segments. i.e.
      // '//Network'            -> ok
      // '//Network/Ubuntu'     -> ok
      // '//Network/Ubuntu/bin' -> NOT OK
      final int idx = name.indexOf('/', 2);
      start = idx == -1 ? 2 : idx + 1;
    }
    else if (name.charAt(0) == '/') {
      start = 1;
    }
    if (name.endsWith(URLUtil.SCHEME_SEPARATOR)) {
      end -= URLUtil.SCHEME_SEPARATOR.length();
    }
    if (StringUtil.containsAnyChar(name, FS_SEPARATORS, start, end)) {
      throw new IllegalArgumentException("Must not intern long path: '" + name + "'");
    }
  }

  /** Generally, I see this check as assistance in testing */
  static boolean isFileNameSanityCheckEnabledByDefault() {
    boolean enabled = ApplicationManagerEx.isInIntegrationTest();
    Application app = ApplicationManager.getApplication();
    if (app != null) {
      enabled = app.isUnitTestMode() || app.isEAP() || app.isInternal();
    }
    return getBooleanProperty("vfs.name-cache.check-names", enabled);
  }


  private AutoCloseable setupReportingToOpenTelemetry() {
    final Meter meter = TelemetryManager.getInstance().getMeter(VFS);

    var queriesCounter = meter.counterBuilder("FileNameCache.queries").buildObserver();
    var fastMissesCounter = meter.counterBuilder("FileNameCache.fastMisses").buildObserver();
    var totalMissesCounter = meter.counterBuilder("FileNameCache.totalMisses").buildObserver();

    return meter.batchCallback(() -> {
      queriesCounter.record(requestsCount.longValue());
      fastMissesCounter.record(fastCacheMissesCount.longValue());
      totalMissesCounter.record(totalCacheMissesCount.longValue());
    }, queriesCounter, fastMissesCounter, totalMissesCounter);
  }
}
