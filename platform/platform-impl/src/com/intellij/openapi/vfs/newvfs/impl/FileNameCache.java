// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.diagnostic.telemetry.TraceManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.IntSLRUCache;
import com.intellij.util.containers.IntObjectLRUMap;
import com.intellij.util.io.URLUtil;
import io.opentelemetry.api.metrics.Meter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public final class FileNameCache {
  @SuppressWarnings("unchecked") private static final IntSLRUCache<CharSequence>[] ourNameCache = new IntSLRUCache[16];

  static {
    initialize();
  }

  public static void drop() {
    Arrays.fill(ourArrayCache, null);
    initialize();
  }

  private static void initialize() {
    final int protectedSize = 40000 / ourNameCache.length;
    final int probationalSize = 20000 / ourNameCache.length;
    for (int i = 0; i < ourNameCache.length; ++i) {
      ourNameCache[i] = new IntSLRUCache<>(protectedSize, probationalSize);
    }
  }

  private static final String FS_SEPARATORS = "/" + (File.separatorChar == '/' ? "" : File.separatorChar);

  public static int storeName(@NotNull String name) {
    assertShortFileName(name);
    final int idx = FSRecords.getNameId(name);
    cacheData(name, idx, calcStripeIdFromNameId(idx));
    return idx;
  }

  private static void assertShortFileName(@NotNull String name) {
    if (name.length() <= 1) return;
    int start = 0;
    int end = name.length();
    if (SystemInfo.isWindows && name.startsWith("//")) {  // Windows UNC: //Network/Ubuntu
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

  @NotNull
  private static IntObjectLRUMap.MapEntry<CharSequence> cacheData(CharSequence name, int nameId, int stripe) {
    if (name == null) {
      throw FSRecords.handleError(
        new RuntimeException("VFS name enumerator corrupted: nameId(=" + nameId + ") is not found in enumerator (=null)"));
    }

    IntSLRUCache<CharSequence> cache = ourNameCache[stripe];
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (cache) {
      return cache.cacheEntry(nameId, name);
    }
  }

  private static int calcStripeIdFromNameId(int id) {
    int h = id;
    h -= h << 6;
    h ^= h >> 17;
    h -= h << 9;
    h ^= h << 4;
    h -= h << 3;
    h ^= h << 10;
    h ^= h >> 15;
    return h % ourNameCache.length;
  }


  private static final int ourLOneSize = 1024;
  @SuppressWarnings("unchecked")
  private static final IntObjectLRUMap.MapEntry<CharSequence>[] ourArrayCache = new IntObjectLRUMap.MapEntry[ourLOneSize];


  private static final boolean ourTrackStats = true;
  private static final AtomicInteger ourQueries = new AtomicInteger();
  private static final AtomicInteger ourFastCacheMisses = new AtomicInteger();
  private static final AtomicInteger ourTotalCacheMisses = new AtomicInteger();

  static {
    if (ourTrackStats) {
      setupReportingToOpenTelemetry();
    }
  }


  @FunctionalInterface
  public interface NameComputer {
    CharSequence compute(int id) throws IOException;
  }

  @NotNull
  private static CharSequence getVFileName(int nameId, @NotNull NameComputer computeName) throws IOException {
    assert nameId > 0 : nameId;

    if (ourTrackStats) {
      ourQueries.incrementAndGet();
    }

    int l1 = nameId % ourLOneSize;
    IntObjectLRUMap.MapEntry<CharSequence> entry = ourArrayCache[l1];
    if (entry != null && entry.key == nameId) {
      return entry.value;
    }

    if (ourTrackStats) {
      ourFastCacheMisses.incrementAndGet();
    }

    final int stripe = calcStripeIdFromNameId(nameId);
    IntSLRUCache<CharSequence> cache = ourNameCache[stripe];
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (cache) {
      entry = cache.getCachedEntry(nameId);
    }
    if (entry == null) {
      if (ourTrackStats) {
        ourTotalCacheMisses.incrementAndGet();
      }
      entry = cacheData(computeName.compute(nameId), nameId, stripe);
    }
    ourArrayCache[l1] = entry;
    return entry.value;
  }

  @NotNull
  public static CharSequence getVFileName(int nameId) {
    try {
      return getVFileName(nameId, FSRecords::getNameByNameId);
    }
    catch (IOException e) {
      throw new RuntimeException(e); // actually will be caught in getNameByNameId
    }
  }

  private static void setupReportingToOpenTelemetry() {
    final Meter meter = TraceManager.INSTANCE.getMeter("vfs");

    var queriesCounter = meter.counterBuilder("FileNameCache.queries").buildObserver();
    var fastMissesCounter = meter.counterBuilder("FileNameCache.fastMisses").buildObserver();
    var totalMissesCounter = meter.counterBuilder("FileNameCache.totalMisses").buildObserver();

    meter.batchCallback(() -> {
      queriesCounter.record(ourQueries.longValue());
      fastMissesCounter.record(ourFastCacheMisses.longValue());
      totalMissesCounter.record(ourTotalCacheMisses.longValue());
    }, queriesCounter, fastMissesCounter, totalMissesCounter);
  }
}
