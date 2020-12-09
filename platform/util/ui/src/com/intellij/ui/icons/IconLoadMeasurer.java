// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.icons;

import com.intellij.diagnostic.StartUpMeasurer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class IconLoadMeasurer {
  public static final Counter svgDecoding = new Counter("svg-decode");
  private static final Counter svgLoading = new Counter("svg-load");

  public static final Counter svgPreBuiltLoad = new Counter("svg-prebuilt");
  public static final Counter svgCacheWrite = new Counter("svg-cache-write");
  public static final Counter svgCacheRead = new Counter("svg-cache-read");

  public static final Counter pngDecoding = new Counter("png-decode");
  private static final Counter pngLoading = new Counter("png-load");

  private static final Counter findIcon = new Counter("find-icon");
  private static final Counter findIconLoad = new Counter("find-icon-load");

  private static final Counter loadFromUrl = new Counter("load-from-url");
  private static final Counter loadFromResources = new Counter("load-from-resource");

  @NotNull
  public static List<Counter> getStats() {
    return Arrays.asList(findIcon, findIconLoad,
                         loadFromUrl, loadFromResources,
                         svgLoading, svgDecoding, svgPreBuiltLoad, svgCacheRead, svgCacheWrite,
                         pngLoading, pngDecoding);
  }

  public static void addLoading(boolean isSvg, long start) {
    (isSvg ? svgLoading : pngLoading).addDuration(StartUpMeasurer.getCurrentTime() - start);
  }

  public static void addFindIcon(long start) {
    findIcon.addDuration(StartUpMeasurer.getCurrentTime() - start);
  }

  public static void addFindIconLoad(long start) {
    findIconLoad.addDuration(StartUpMeasurer.getCurrentTime() - start);
  }

  public static void addLoadFromUrl(long start) {
    loadFromUrl.addDuration(StartUpMeasurer.getCurrentTime() - start);
  }

  public static void addLoadFromResources(long start) {
    loadFromResources.addDuration(StartUpMeasurer.getCurrentTime() - start);
  }

  public static final class Counter {
    private final String type;

    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicLong totalTime = new AtomicLong();

    public Counter(@NotNull @NonNls String type) {
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
      if (startTime > 0) {
        addDuration(StartUpMeasurer.getCurrentTime() - startTime);
      }
    }

    public void addDuration(long duration) {
      counter.incrementAndGet();
      if (duration > 0) {
        totalTime.getAndAdd(duration);
      }
    }
  }
}
