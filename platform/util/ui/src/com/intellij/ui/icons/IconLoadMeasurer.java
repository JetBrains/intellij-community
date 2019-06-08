// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.icons;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class IconLoadMeasurer {
  private final ImageType type;

  private final AtomicInteger counter = new AtomicInteger();
  private final AtomicInteger totalTime = new AtomicInteger();

  private static final IconLoadMeasurer decodingSvg = new IconLoadMeasurer(ImageType.SVG);
  private static final IconLoadMeasurer decodingPng = new IconLoadMeasurer(ImageType.IMG);

  private static final IconLoadMeasurer loadingSvg = new IconLoadMeasurer(ImageType.SVG);
  private static final IconLoadMeasurer loadingPng = new IconLoadMeasurer(ImageType.IMG);

  public IconLoadMeasurer(@NotNull ImageType type) {
    this.type = type;
  }

  @NotNull
  public static List<IconLoadMeasurer> getStats() {
    return Arrays.asList(loadingSvg, decodingSvg, loadingPng, decodingPng);
  }

  public static void addDecoding(@NotNull ImageType type, int duration) {
    ((type == ImageType.SVG) ? decodingSvg : decodingPng).addDuration(duration);
  }

  public static void addLoading(@NotNull ImageType type, int duration) {
    ((type == ImageType.SVG) ? loadingSvg : loadingPng).addDuration(duration);
  }

  public int getCounter() {
    return counter.get();
  }

  public int getTotalTime() {
    return totalTime.get();
  }

  @NotNull
  public ImageType getType() {
    return type;
  }

  private void addDuration(int duration) {
    counter.incrementAndGet();
    totalTime.updateAndGet(current -> current + duration);
  }
}
