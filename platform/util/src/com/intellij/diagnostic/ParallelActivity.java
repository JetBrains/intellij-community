// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// non-sequential and repeated items
public enum ParallelActivity {
  PREPARE_APP_INIT("prepareAppInitActivity"), PRELOAD_ACTIVITY("preloadActivity"),
  APP_OPTIONS_TOP_HIT_PROVIDER("appOptionsTopHitProvider"), PROJECT_OPTIONS_TOP_HIT_PROVIDER("projectOptionsTopHitProvider"),
  COMPONENT("component"), SERVICE("service"), EXTENSION("extension"),
  PROJECT_OPEN_HANDLER("openHandler"),

  POST_STARTUP_ACTIVITY("projectPostStartupActivity"),
  GC("GC"),
  REOPENING_EDITOR("reopeningEditor")
  ;

  public static final long MEASURE_THRESHOLD = TimeUnit.MILLISECONDS.toNanos(10);

  private final String jsonName;

  private final AtomicInteger counter = new AtomicInteger();

  ParallelActivity(@NotNull String jsonName) {
    this.jsonName = jsonName;
  }

  @NotNull
  public String getJsonName() {
    return jsonName;
  }

  @NotNull
  public Activity start(@NotNull String name) {
    return ActivityImpl.createParallelActivity(this, name);
  }

  @NotNull
  public Activity start(@NotNull String name, @NotNull StartUpMeasurer.Level level) {
    ActivityImpl activity = new ActivityImpl(name, StartUpMeasurer.getCurrentTime(), /* parent = */ null, level, null);
    activity.setParallelActivity(this);
    return activity;
  }

  @NotNull
  public Activity start(@NotNull String name, @Nullable StartUpMeasurer.Level level, @Nullable String pluginId) {
    ActivityImpl activity = new ActivityImpl(name, StartUpMeasurer.getCurrentTime(), /* parent = */ null, level, pluginId);
    activity.setParallelActivity(this);
    return activity;
  }

  public long record(long start, @Nullable Class<?> clazz, @Nullable String pluginId) {
    return record(start, clazz, /* level = */ null, pluginId);
  }

  /**
   * Default threshold is applied.
   */
  public long record(long start, @NotNull Class<?> clazz, @Nullable StartUpMeasurer.Level level) {
    return record(start, clazz, level, null);
  }

  /**
   * Default threshold is applied.
   */
  public long record(long start, @Nullable Class<?> clazz, @Nullable StartUpMeasurer.Level level, String pluginId) {
    long end = StartUpMeasurer.getCurrentTime();
    long duration = end - start;
    if (duration <= MEASURE_THRESHOLD) {
      return duration;
    }

    ActivityImpl item = new ActivityImpl(clazz == null ? Integer.toString(counter.incrementAndGet()) : clazz.getName(), start, /* parent = */ null, level, pluginId);
    item.setParallelActivity(this);
    item.setEnd(end);
    StartUpMeasurer.add(item);
    return duration;
  }
}
