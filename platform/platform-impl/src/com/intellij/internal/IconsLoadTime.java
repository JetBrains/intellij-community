/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.FieldAccessor;
import com.intellij.util.ImageLoader;
import com.intellij.util.ImageLoader.LoadFunction;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Logs load time statistics for PNG/SVG images, such as: average, median, ide startup total, first N icons total.
 *
 * @author tav
 */
public class IconsLoadTime extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.internal.IconsLoadTime");

  private static final boolean IS_INTERNAL_MODE = Boolean.valueOf(System.getProperty("idea.is.internal")).booleanValue();
  private static final int STATS_LIMIT = 10000;
  private static final int FIXED_SCOPE = 100; // log stats for a first fixed number of icons

  private static final List<Integer> stats = IS_INTERNAL_MODE ? Collections.synchronizedList(new LinkedList<>()) : null; // load time per icon

  static {
    if (IS_INTERNAL_MODE) new FieldAccessor<>(ImageLoader.class, "measureLoad").set(null, (LoadFunction)func -> measure(Objects.requireNonNull(func)));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    log(false);
  }

  private static void log(boolean measureStartupLoad) {
    if (stats == null || stats.isEmpty()) return;

    int size = stats.size();
    long sum = stats.stream().mapToInt(Integer::intValue).sum();
    long average = sum / size;
    long median = (size % 2 == 0) ? stats.get(size / 2 - 1) + stats.get(size / 2) : stats.get(size / 2);

    Function<Long, String> ms = (nano) -> String.format("%.02fms", nano / 1000000f);

    LOG.info((Registry.is("ide.svg.icon") ? "SVG" : "PNG") +
             " load time: " +
             (measureStartupLoad ? "ide_startup=" : "total=") + ms.apply(sum) +
             ", average=" + ms.apply(average) +
             ", median=" + ms.apply(median) +
             "; number of icons: " + size);
  }

  private static Image measure(LoadFunction func) throws IOException {
    boolean measure = stats.size() < STATS_LIMIT;
    long t = measure ? System.nanoTime() : 0;

    Image img = func.load(null);

    if (measure) {
      stats.add((int)(System.nanoTime() - t));
      if (stats.size() == FIXED_SCOPE) log(false);
    }
    return img;
  }

  public static class StartupLoadTime implements StartupActivity, DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
      if (IS_INTERNAL_MODE) log(true);
    }
  }
}
