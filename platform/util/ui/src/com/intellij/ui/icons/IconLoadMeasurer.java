// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.icons;

import com.intellij.diagnostic.StartUpMeasurer;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class IconLoadMeasurer {
  private static final Counter svgDecoding = new Counter("svg-decode");
  private static final Counter svgLoading = new Counter("svg-load");

  public static final Counter svgPreBuiltLoad = new Counter("svg-prebuilt");
  public static final Counter svgCacheWrite = new Counter("svg-cache-write");
  public static final Counter svgCacheRead = new Counter("svg-cache-read");

  private static final Counter decodingPng = new Counter("png-decode");
  private static final Counter loadingPng = new Counter("png-load");

  private static final Counter findIcon = new Counter("find-icon");
  private static final Counter findIconLoad = new Counter("find-icon-load");

  private static final Counter loadFromUrl = new Counter("load-from-url");
  private static final Counter loadFromResources = new Counter("load-from-resource");

  @NotNull
  public static List<Counter> getStats() {
    return Arrays.asList(findIcon, findIconLoad,
                         loadFromUrl, loadFromResources,
                         svgLoading, svgDecoding, svgPreBuiltLoad, svgCacheRead, svgCacheWrite,
                         loadingPng, decodingPng);
  }

  public static void addDecoding(@NotNull ImageType type, int duration) {
    ((type == ImageType.SVG) ? svgDecoding : decodingPng).addDuration(duration);
  }

  public static void addLoading(@NotNull ImageType type, int duration) {
    ((type == ImageType.SVG) ? svgLoading : loadingPng).addDuration(duration);
  }

  public static void addFindIcon(long duration) {
    findIcon.addDuration(duration);
  }

  public static void addFindIconLoad(long duration) {
    findIconLoad.addDuration(duration);
  }

  public static void addLoadFromUrl(long duration) {
    loadFromUrl.addDuration(duration);
  }

  public static void addLoadFromResources(long duration) {
    loadFromResources.addDuration(duration);
  }

  public static final class Counter {
    private final String type;

    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicLong totalTime = new AtomicLong();

    public Counter(@NotNull String type) {
      this.type = type;
    }

    @NotNull
    public String getType() {
      return type;
    }

    public int getCounter() {
      return counter.get();
    }

    public long getTotalTime() {
      return totalTime.get();
    }

    public void addDurationStartedAt(long startTime) {
      if (startTime <= 0) return;
      addDuration(StartUpMeasurer.getCurrentTime() - startTime);
    }

    public void addDuration(long duration) {
      counter.incrementAndGet();
      if (duration > 0) {
        totalTime.getAndAdd(duration);
      }
    }
  }
}
