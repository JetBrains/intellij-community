// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.icons.ImageDescriptor;
import com.intellij.ui.icons.ImageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Logs load time statistics for PNG/SVG images, such as: average, median, ide startup total, first N icons total.
 *
 * @author tav
 */
public final class IconsLoadTime extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.internal.IconsLoadTime");

  private static final int STATS_LIMIT = 10000;
  private static final int FIXED_SCOPE = 100; // log stats for a first fixed number of icons

  // load time per icon
  private static final List<Integer> statsSVG = new ArrayList<>();
  private static final List<Integer> statsPNG = new ArrayList<>();

  static {
    if (Boolean.getBoolean("idea.measure.icon.load.time")) {
      ImageDescriptor.setLoadTimeConsumer(IconsLoadTime::measure);
    }
  }

  public static class StatData {
    public final ImageType type;
    public final boolean startup;
    public final int count;

    // millis
    public final float totalTime;
    public final float averageTime;
    public final float medianTime;

    private StatData(@NotNull ImageType type, boolean startup, int totalTime, int averageTime, int medianTime, int count) {
      this.type = type;
      this.startup = startup;
      this.count = count;

      this.totalTime = totalTime / 1000000f;
      this.averageTime = averageTime / 1000000f;
      this.medianTime = medianTime / 1000000f;
    }

    @Override
    public String toString() {
      return type + " load time: " +
             (startup ? "ide_startup=" : "total=") + String.format("%.02fms", totalTime) +
             ", average=" + String.format("%.02fms", averageTime) +
             ", median=" + String.format("%.02fms", medianTime) +
             "; number of icons: " + count;
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    log(false);
  }

  public static void log(boolean measureStartupLoad) {
    log(measureStartupLoad, ImageType.IMG);
    log(measureStartupLoad, ImageType.SVG);
  }

  private static void log(boolean measureStartupLoad, @NotNull ImageType type) {
    StatData data = getStatData(measureStartupLoad, type);
    if (data != null) LOG.info(data.toString());
  }

  @Nullable
  public static StatData getStatData(boolean measureStartupLoad, @NotNull ImageType type) {
    List<Integer> stats = getStats(type);
    if (stats.isEmpty()) {
      return null;
    }

    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (stats) {
      int size = stats.size();
      int sum = stats.stream().mapToInt(Integer::intValue).sum();
      int average = sum / size;
      int median = (size % 2 == 0) ? stats.get(size / 2 - 1) + stats.get(size / 2) : stats.get(size / 2);
      return new StatData(type, measureStartupLoad, sum, average, median, size);
    }
  }

  private static void measure(@NotNull ImageType type, int duration) {
    List<Integer> stats = getStats(type);
    if (stats.size() > STATS_LIMIT) {
      ImageDescriptor.setLoadTimeConsumer(null);
    }

    int size;
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (stats) {
      stats.add(duration);
      size = stats.size();
    }
    if (size == FIXED_SCOPE) {
      log(false, type);
    }
  }

  @NotNull
  private static List<Integer> getStats(@NotNull ImageType type) {
    return type == ImageType.SVG ? statsSVG : statsPNG;
  }
}
