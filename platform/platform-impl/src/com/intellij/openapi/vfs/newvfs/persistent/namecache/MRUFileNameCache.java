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
import java.util.concurrent.ThreadLocalRandom;
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

  /**
   * Cache is really very fast, so even 1-2 stats counters atomic increments significantly affect its
   * performance. But disable the stats is also undesirable -- name caching is a hotspot, so it is of
   * real interest how much names we do resolve, and how well caching works.
   * So lets track only each 64th cache lookup, by using (nameId % 64 == salt) there salt is randomly
   * chosen on each session -- to avoid long-term favoritism for any specific nameIds group -- and
   * interpolate actual lookup count on reporting.
   * Such a 'sampling' increases the noise (stdev), but typical lookups count ~10-100e6, so it doesn't
   * matter much.
   */
  private static final int TRACK_EACH_NTH_LOOKUP_STATS = 64;
  private static final int TRACK_STATS_MASK = TRACK_EACH_NTH_LOOKUP_STATS - 1;
  private static final int TRACK_STATS_SALT = ThreadLocalRandom.current().nextInt(TRACK_EACH_NTH_LOOKUP_STATS);

  //TODO RC: cache size is better be ctor parameter
  private static final int MRU_CACHE_SIZE = 1024 * 72;

  //The cache utilizes a rare case of 'benign data race' in JMM. Basically, if you have an array of
  // primitives or immutable types, then reads & writes to that array never produces corrupted data.
  // I.e. each value thread reads from the array cell is the value that some thread has written to the
  // array cell at some (maybe later!) moment of program life. If code is ready to process any of
  // possible values -- code is correct, regardless of the fact that there is a data race.
  // We piggyback on this property: mruCache[i] could be null (initial value), or any entry such that
  // entry.nameId is hashed to i -- and code in .valueOf() method is ready to any such value.

  private final CacheEntryNameWithId[] mruCache = new CacheEntryNameWithId[MRU_CACHE_SIZE];

  private final @NotNull DataEnumeratorEx<String> namesEnumerator;

  private final boolean checkFileNamesSanity;

  //=========== monitoring: =======================================================
  private final AtomicInteger cacheHitsCount = new AtomicInteger();
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
    int mruCacheEntryIndex = toIndex(nameId);
    CacheEntryNameWithId entry = mruCache[mruCacheEntryIndex];
    if (entry != null && entry.nameId == nameId) {
      return;//already cached
    }
    mruCache[mruCacheEntryIndex] = new CacheEntryNameWithId(nameId, name);
  }

  @Override
  public @NotNull String valueOf(int nameId) throws IOException {
    assert nameId > 0 : nameId;

    boolean trackLookup = shouldTrackLookup(nameId);

    int mruCacheEntryIndex = toIndex(nameId);
    CacheEntryNameWithId entry = mruCache[mruCacheEntryIndex];
    if (entry != null && entry.nameId == nameId) {
      if (trackLookup) {
        cacheHitsCount.incrementAndGet();
      }
      return entry.name;
    }

    if (trackLookup) {
      cacheMissesCount.incrementAndGet();
    }

    String name = namesEnumerator.valueOf(nameId);
    if (name == null) {
      throw new IOException("VFS name enumerator corrupted: nameId(=" + nameId + ") is not found in enumerator (=null)");
    }
    mruCache[mruCacheEntryIndex] = new CacheEntryNameWithId(nameId, name);

    return name;
  }

  private static boolean shouldTrackLookup(int nameId) {
    return TRACK_STATS && (nameId & TRACK_STATS_MASK) == TRACK_STATS_SALT;
  }

  private static int toIndex(int nameId) {
    int hash = Math.abs(nameId * 0x9E3779B9);//fibbonacci hash
    return hash % MRU_CACHE_SIZE;
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
      long cacheMisses = cacheMissesCount.longValue() * TRACK_EACH_NTH_LOOKUP_STATS;
      long totalCacheRequestsServed = (cacheHitsCount.longValue() * TRACK_EACH_NTH_LOOKUP_STATS) + cacheMisses;
      queriesCounter.record(totalCacheRequestsServed);
      totalMissesCounter.record(cacheMisses);
    }, queriesCounter, totalMissesCounter);
  }

  private static final class CacheEntryNameWithId {
    public final int nameId;
    public final String name;

    CacheEntryNameWithId(int nameId, String name) {
      this.nameId = nameId;
      this.name = name;
    }
  }
}
