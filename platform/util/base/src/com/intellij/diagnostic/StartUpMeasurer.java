// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class StartUpMeasurer {
  static final AtomicReference<LoadingState> currentState = new AtomicReference<>(LoadingState.BOOTSTRAP);

  public static final long MEASURE_THRESHOLD = TimeUnit.MILLISECONDS.toNanos(10);

  // `what + noun` is used as scheme for name to make analyzing easier (to visually group - `components loading/initialization/etc`,
  // not to put common part of name to end of).
  // It is not serves only display purposes - it is IDs. Visualizer and another tools to analyze data uses phase IDs,
  // so, any changes must be discussed across all involved and reflected in changelog (see `format-changelog.md`).
  public static final class Activities {
    // actually, now it is also registers services, not only components,but it doesn't worth to rename
    public static final String REGISTER_COMPONENTS_SUFFIX = "component registration";
    public static final String CREATE_COMPONENTS_SUFFIX = "component creation";

    public static final String PROJECT_DUMB_POST_START_UP_ACTIVITIES = "project post-startup dumb-aware activities";
    public static final String EDITOR_RESTORING = "editor restoring";
    public static final String EDITOR_RESTORING_TILL_PAINT = "editor restoring till paint";
  }

  @SuppressWarnings("StaticNonFinalField")
  public static boolean measuringPluginStartupCosts = true;

  public static void stopPluginCostMeasurement() {
    measuringPluginStartupCosts = false;
  }

  private static long startTime = System.nanoTime();

  private static final Queue<ActivityImpl> items = new ConcurrentLinkedQueue<>();

  private static boolean isEnabled = true;

  public static boolean isEnabled() {
    return isEnabled;
  }

  @TestOnly
  public static void disable() {
    isEnabled = false;
  }

  @ApiStatus.Internal
  public static final Map<String, Object2LongMap<String>> pluginCostMap = new ConcurrentHashMap<>();

  @ApiStatus.Internal
  public volatile static Activity appInitPreparationActivity;

  public static long getCurrentTime() {
    return System.nanoTime();
  }

  public static long getCurrentTimeIfEnabled() {
    return isEnabled ? System.nanoTime() : -1;
  }

  /**
   * Since start in ms.
   */
  public static long sinceStart() {
    return TimeUnit.NANOSECONDS.toMillis(getCurrentTime() - startTime);
  }

  /**
   * The instant events correspond to something that happens but has no duration associated with it.
   * See <a href="https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/preview#heading=h.lenwiilchoxp">this document</a> for details.
   *
   * Scope is not supported, reported as global.
   */
  public static void addInstantEvent(@NonNls @NotNull String name) {
    if (!isEnabled) {
      return;
    }

    ActivityImpl activity = new ActivityImpl(name, getCurrentTime(), null, null);
    activity.setEnd(-1);
    addActivity(activity);
  }

  public static @NotNull Activity startActivity(@NonNls @NotNull String name, @NotNull ActivityCategory category) {
    return new ActivityImpl(name, getCurrentTime(), /* parent = */ null, /* pluginId = */ null, category);
  }

  public static @NotNull Activity startActivity(@NonNls @NotNull String name) {
    return new ActivityImpl(name, getCurrentTime(), /* parent = */ null, /* pluginId = */ null, ActivityCategory.DEFAULT);
  }

  public static @NotNull Activity startActivity(@NonNls @NotNull String name, @NotNull ActivityCategory category, @Nullable String pluginId) {
    return new ActivityImpl(name, getCurrentTime(), /* parent = */ null, /* pluginId = */ pluginId, category);
  }

  /**
   * Default threshold is applied.
   */
  public static long addCompletedActivity(long start, @NotNull Class<?> clazz, @NotNull ActivityCategory category, @Nullable String pluginId) {
    return addCompletedActivity(start, clazz, category, pluginId, -1);
  }

  public static long addCompletedActivity(long start, @NotNull Class<?> clazz, @NotNull ActivityCategory category, @Nullable String pluginId, long threshold) {
    if (!isEnabled) {
      return -1;
    }

    long end = getCurrentTime();
    long duration = end - start;
    if (duration <= threshold) {
      return duration;
    }

    addCompletedActivity(start, end, clazz.getName(), category, pluginId);
    return duration;
  }

  /**
   * Default threshold is applied.
   */
  public static long addCompletedActivity(long start, @NonNls @NotNull String name, @NotNull ActivityCategory category, String pluginId) {
    long end = getCurrentTime();
    long duration = end - start;
    if (duration <= MEASURE_THRESHOLD) {
      return duration;
    }

    addCompletedActivity(start, end, name, category, pluginId);
    return duration;
  }

  public static void addCompletedActivity(long start, long end, @NonNls @NotNull String name, @NotNull ActivityCategory category, String pluginId) {
    if (!isEnabled) {
      return;
    }

    ActivityImpl item = new ActivityImpl(name, start, /* parent = */ null, pluginId, category);
    item.setEnd(end);
    addActivity(item);
  }

  public static void setCurrentState(@NotNull LoadingState state) {
    LoadingState old = currentState.getAndSet(state);
    if (old.compareTo(state) > 0) {
      BiConsumer<String, Throwable> errorHandler = LoadingState.errorHandler;
      if (errorHandler != null) {
        errorHandler.accept("New state " + state + " cannot precede old " + old, new Throwable());
      }
    }
    stateSet(state);
  }

  public static void compareAndSetCurrentState(@NotNull LoadingState expectedState, @NotNull LoadingState newState) {
    if (currentState.compareAndSet(expectedState, newState)) {
      stateSet(newState);
    }
  }

  private static void stateSet(@NotNull LoadingState state) {
    addInstantEvent(state.displayName);
  }

  @ApiStatus.Internal
  public static void processAndClear(boolean isContinueToCollect, @NotNull Consumer<? super ActivityImpl> consumer) {
    isEnabled = isContinueToCollect;

    while (true) {
      ActivityImpl item = items.poll();
      if (item == null) {
        break;
      }

      consumer.accept(item);
    }
  }

  @ApiStatus.Internal
  public static long getStartTime() {
    return startTime;
  }

  static void addActivity(@NotNull ActivityImpl activity) {
    if (isEnabled) {
      items.add(activity);
    }
  }

  @ApiStatus.Internal
  public static void addTimings(@NotNull LinkedHashMap<String, Long> timings, @NotNull String groupName) {
    if (!items.isEmpty()) {
      throw new IllegalStateException("addTimings must be not called if some events were already added using API");
    }

    if (timings.isEmpty()) {
      return;
    }

    List<Map.Entry<String, Long>> entries = new ArrayList<>(timings.entrySet());

    ActivityImpl parent = new ActivityImpl(groupName, entries.get(0).getValue(), null, null);
    parent.setEnd(getCurrentTime());

    for (int i = 0; i < entries.size(); i++) {
      long start = entries.get(i).getValue();
      if (start < startTime) {
        startTime = start;
      }

      ActivityImpl activity = new ActivityImpl(entries.get(i).getKey(), start, parent, null);
      activity.setEnd(i == entries.size() - 1 ? parent.getEnd() : entries.get(i + 1).getValue());
      items.add(activity);
    }
    items.add(parent);
  }

  @ApiStatus.Internal
  public static void addPluginCost(@NonNls @NotNull String pluginId, @NonNls @NotNull String phase, long time) {
    if (isMeasuringPluginStartupCosts()) {
      doAddPluginCost(pluginId, phase, time, pluginCostMap);
    }
  }

  public static boolean isMeasuringPluginStartupCosts() {
    return measuringPluginStartupCosts;
  }

  @ApiStatus.Internal
  public static void doAddPluginCost(@NonNls @NotNull String pluginId,
                                     @NonNls @NotNull String phase,
                                     long time,
                                     @NotNull Map<String, Object2LongMap<String>> pluginCostMap) {
    Object2LongMap<String> costPerPhaseMap = pluginCostMap.computeIfAbsent(pluginId, __ -> new Object2LongOpenHashMap<>());
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (costPerPhaseMap) {
      costPerPhaseMap.mergeLong(phase, time, Math::addExact);
    }
  }
}