// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.List;

/**
 * See icon-loading-stat.svg to understand how icon loading is measured.
 */
@ApiStatus.Internal
@VisibleForTesting
public final class IconsLoadTime extends DumbAwareAction {

  private static final Logger LOG = Logger.getInstance(IconsLoadTime.class);

  // load time per icon
  private static final IntList svgStats = new IntArrayList();
  private static final IntList pngStats = new IntArrayList();

  public static final class StatData {
    public final boolean isSvg;
    public final boolean startup;
    public final int count;

    // millis
    public final float totalTime;
    public final float averageTime;
    public final float medianTime;

    private StatData(boolean isSvg, boolean startup, int totalTime, int averageTime, int medianTime, int count) {
      this.isSvg = isSvg;
      this.startup = startup;
      this.count = count;

      this.totalTime = totalTime / 1000000f;
      this.averageTime = averageTime / 1000000f;
      this.medianTime = medianTime / 1000000f;
    }

    @Override
    public String toString() {
      return "load time: " +
             (startup ? "ide_startup=" : "total=") + String.format("%.02fms", totalTime) +
             ", average=" + String.format("%.02fms", averageTime) +
             ", median=" + String.format("%.02fms", medianTime) +
             ", isSvg=" + isSvg +
             "; number of icons: " + count;
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    log(false);
  }

  public static void log(boolean measureStartupLoad) {
    log(measureStartupLoad, false);
    log(measureStartupLoad, true);
  }

  private static void log(boolean measureStartupLoad, boolean isSvg) {
    StatData data = getStatData(measureStartupLoad, isSvg);
    if (data != null) LOG.info(data.toString());
  }

  public static @Nullable StatData getStatData(boolean measureStartupLoad, boolean isSvg) {
    List<Integer> stats = getStats(isSvg);
    if (stats.isEmpty()) {
      return null;
    }

    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (stats) {
      int size = stats.size();
      int sum = stats.stream().mapToInt(Integer::intValue).sum();
      int average = sum / size;
      int median = (size % 2 == 0) ? stats.get(size / 2 - 1) + stats.get(size / 2) : stats.get(size / 2);
      return new StatData(isSvg, measureStartupLoad, sum, average, median, size);
    }
  }

  private static @NotNull IntList getStats(boolean isSvg) {
    return isSvg ? svgStats : pngStats;
  }
}
