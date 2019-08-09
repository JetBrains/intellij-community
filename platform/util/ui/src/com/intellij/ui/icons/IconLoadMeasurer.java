// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.icons;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class IconLoadMeasurer {
  private static final Counter decodingSvg = new Counter("svg-decode");
  private static final Counter decodingPng = new Counter("png-decode");

  private static final Counter loadingSvg = new Counter("svg-load");
  private static final Counter loadingPng = new Counter("png-load");

  private static final Counter findIcon = new Counter("find-icon");
  private static final Counter findIconLoad = new Counter("find-icon-load");

  private static final Counter loadFromUrl = new Counter("load-from-url");
  private static final Counter loadFromResources = new Counter("load-from-resource");

  @NotNull
  public static List<Counter> getStats() {
    return Arrays.asList(findIcon, findIconLoad, loadFromUrl, loadFromResources, loadingSvg, decodingSvg, loadingPng, decodingPng);
  }

  public static void addDecoding(@NotNull ImageType type, int duration) {
    ((type == ImageType.SVG) ? decodingSvg : decodingPng).addDuration(duration);
  }

  public static void addLoading(@NotNull ImageType type, int duration) {
    ((type == ImageType.SVG) ? loadingSvg : loadingPng).addDuration(duration);
  }

  public static void addFindIcon(int duration) {
    findIcon.addDuration(duration);
  }

  public static void addFindIconLoad(int duration) {
    findIconLoad.addDuration(duration);
  }

  public static void addLoadFromUrl(int duration) {
    loadFromUrl.addDuration(duration);
  }

  public static void addLoadFromResources(int duration) {
    loadFromResources.addDuration(duration);
  }

  public static final class Counter {
    private final String type;

    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicInteger totalTime = new AtomicInteger();

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

    public int getTotalTime() {
      return totalTime.get();
    }

    public void addCounter() {
      counter.incrementAndGet();
    }

    public void addDuration(int duration) {
      counter.incrementAndGet();
      if (duration > 0) {
        totalTime.updateAndGet(current -> current + duration);
      }
    }
  }
}
