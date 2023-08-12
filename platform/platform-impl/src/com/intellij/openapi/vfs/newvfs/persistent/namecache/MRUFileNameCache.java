// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.namecache;

import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.util.io.DataEnumeratorEx;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.platform.diagnostic.telemetry.PlatformScopesKt.VFS;
import static com.intellij.util.SystemProperties.getBooleanProperty;

/**
 * Contrary to {@link SLRUFileNameCache} this cache uses _only_ the MRU fast-path cache, but with
 * big size -- instead of small MRU in front of bigger SLRU
 */
@ApiStatus.Internal
public final class MRUFileNameCache implements FileNameCache {
  private static final boolean TRACK_STATS = getBooleanProperty("vfs.name-cache.track-stats", true);

  //TODO RC: cache size is better be ctor parameter
  private static final int MRU_CACHE_SIZE = 1024 * 64;

  private final CacheEntryNameWithId[] mruCache = new CacheEntryNameWithId[MRU_CACHE_SIZE];

  private final @NotNull DataEnumeratorEx<String> namesEnumerator;

  private final boolean checkFileNamesSanity;

  //=========== monitoring: =======================================================
  private final AtomicInteger requestsCount = new AtomicInteger();
  private final AtomicInteger cacheMissesCount = new AtomicInteger();
  private final @Nullable BatchCallback otelHandlerToClose;


  public MRUFileNameCache(@NotNull DataEnumeratorEx<String> namesEnumerator) {
    this(namesEnumerator, SLRUFileNameCache.isFileNameSanityCheckEnabledByDefault());
  }


  public MRUFileNameCache(@NotNull DataEnumeratorEx<String> namesEnumerator,
                          boolean checkFileNamesSanity) {
    this.namesEnumerator = namesEnumerator;
    this.checkFileNamesSanity = checkFileNamesSanity;

    otelHandlerToClose = TRACK_STATS ?
                         setupReportingToOpenTelemetry() :
                         null;
  }

  @Override
  public int tryEnumerate(@NotNull String name) throws IOException {
    int nameId = namesEnumerator.tryEnumerate(name);
    if (nameId != NULL_ID) {
      cacheData(nameId, name);
    }
    return nameId;
  }

  @Override
  public int enumerate(@NotNull String name) throws IOException {
    if (checkFileNamesSanity) {
      SLRUFileNameCache.assertShortFileName(name);
    }

    int nameId = namesEnumerator.enumerate(name);
    cacheData(nameId, name);
    return nameId;
  }

  private void cacheData(int nameId,
                         @Nullable String name) {
    int mruCacheEntryIndex = nameId % MRU_CACHE_SIZE;
    CacheEntryNameWithId entry = mruCache[mruCacheEntryIndex];
    if (entry != null && entry.nameId == nameId) {
      return;//already cached
    }
    mruCache[mruCacheEntryIndex] = new CacheEntryNameWithId(nameId, name);
  }

  @Override
  public @NotNull String valueOf(int nameId) throws IOException {
    assert nameId > 0 : nameId;

    if (TRACK_STATS) {
      requestsCount.incrementAndGet();
    }

    int mruCacheEntryIndex = nameId % MRU_CACHE_SIZE;
    CacheEntryNameWithId entry = mruCache[mruCacheEntryIndex];
    if (entry != null && entry.nameId == nameId) {
      return entry.name;
    }

    if (TRACK_STATS) {
      cacheMissesCount.incrementAndGet();
    }

    String name = namesEnumerator.valueOf(nameId);
    if (name == null) {
      throw new IOException("VFS name enumerator corrupted: nameId(=" + nameId + ") is not found in enumerator (=null)");
    }
    mruCache[mruCacheEntryIndex] = new CacheEntryNameWithId(nameId, name);

    return name;
  }

  @Override
  public void close() throws Exception {
    if (otelHandlerToClose != null) {
      otelHandlerToClose.close();
    }
  }

  private BatchCallback setupReportingToOpenTelemetry() {
    final Meter meter = TelemetryManager.getInstance().getMeter(VFS);

    var queriesCounter = meter.counterBuilder("FileNameCache.queries").buildObserver();
    var totalMissesCounter = meter.counterBuilder("FileNameCache.totalMisses").buildObserver();

    return meter.batchCallback(() -> {
      queriesCounter.record(requestsCount.longValue());
      totalMissesCounter.record(cacheMissesCount.longValue());
    }, queriesCounter, totalMissesCounter);
  }

  private static class CacheEntryNameWithId {
    public final int nameId;
    public final String name;

    CacheEntryNameWithId(int nameId, String name) {
      this.nameId = nameId;
      this.name = name;
    }
  }
}
