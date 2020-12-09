// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.icons;

import com.intellij.diagnostic.StartUpMeasurer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@ApiStatus.Internal
public final class IconLoadMeasurer {
  public static final Counter svgDecoding = new Counter("svg-decode");
  private static final Counter svgLoading = new Counter("svg-load");

  public static final Counter svgPreBuiltLoad = new Counter("svg-prebuilt");
  public static final Counter svgCacheWrite = new Counter("svg-cache-write");
  public static final Counter svgCacheRead = new Counter("svg-cache-read");

  public static final Counter pngDecoding = new Counter("png-decode");
  private static final Counter pngLoading = new Counter("png-load");

  public static final Counter findIcon = new Counter("find-icon");
  public static final Counter findIconLoad = new Counter("find-icon-load");

  public static final Counter loadFromUrl = new Counter("load-from-url");
  public static final Counter loadFromResources = new Counter("load-from-resource");

  /**
   * Get icon for action. Measured to understand impact.
   */
  public static final Counter actionIcon = new Counter("action-icon");

  public static @NotNull List<Counter> getStats() {
    return Arrays.asList(findIcon, findIconLoad,
                         loadFromUrl, loadFromResources,
                         svgLoading, svgDecoding, svgPreBuiltLoad, svgCacheRead, svgCacheWrite,
                         pngLoading, pngDecoding,
                         actionIcon);
  }

  public static void addLoading(boolean isSvg, long start) {
    (isSvg ? svgLoading : pngLoading).end(start);
  }

  public static final class Counter {
    public final String name;

    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicLong totalDuration = new AtomicLong();

    private Counter(@NotNull @NonNls String name) {
      this.name = name;
    }

    public int getCount() {
      return counter.get();
    }

    public long getTotalDuration() {
      return totalDuration.get();
    }

    public void end(long startTime) {
      if (startTime > 0) {
        long duration = StartUpMeasurer.getCurrentTime() - startTime;
        counter.incrementAndGet();
        totalDuration.getAndAdd(duration);
      }
    }
  }
}
