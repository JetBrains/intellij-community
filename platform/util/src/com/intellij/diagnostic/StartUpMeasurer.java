// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.util.containers.ObjectLongHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public final class StartUpMeasurer {
  // Use constants for better overview of existing phases (and preserve consistent naming).
  // `what + noun` is used as scheme for name to make analyzing easier (to visually group - `components loading/initialization/etc`,
  // not to put common part of name to end of).
  // It is not serves only display purposes - it is IDs. Visualizer and another tools to analyze data uses phase IDs,
  // so, any changes must be discussed across all involved and reflected in changelog (see `format-changelog.md`).
  public static final class Phases {
    public static final String LOAD_MAIN_CLASS = "load main class";

    // this phase name is not fully clear - it is time from `PluginManager.start` to `IdeaApplication.initApplication`
    public static final String PREPARE_TO_INIT_APP = "app initialization preparation";
    public static final String CHECK_SYSTEM_DIR = "check system dirs";
    public static final String LOCK_SYSTEM_DIRS = "lock system dirs";
    public static final String START_LOGGING = "start logging";

    public static final String WAIT_TASKS = "wait tasks";

    public static final String CONFIGURE_LOGGING = "configure logging";

    // this phase name is not fully clear - it is time from `IdeaApplication.initApplication` to `IdeaApplication.run`
    public static final String INIT_APP = "app initialization";

    public static final String PLACE_ON_EVENT_QUEUE = "place on event queue";

    public static final String WAIT_PLUGIN_INIT = "wait plugin initialization";

    // actually, now it is also registers services, not only components,but it doesn't worth to rename
    public static final String REGISTER_COMPONENTS_SUFFIX = "component registration";
    public static final String COMPONENTS_REGISTERED_CALLBACK_SUFFIX = "component registered callback";
    public static final String CREATE_COMPONENTS_SUFFIX = "component creation";

    public static final String APP_INITIALIZED_CALLBACK = "app initialized callback";
    public static final String FRAME_INITIALIZATION = "frame initialization";

    public static final String PROJECT_CONVERSION = "project conversion";
    public static final String PROJECT_BEFORE_LOADED = "project before loaded callbacks";
    public static final String PROJECT_INSTANTIATION = "project instantiation";
    public static final String PROJECT_PRE_STARTUP = "project pre-startup";
    public static final String PROJECT_STARTUP = "project startup";

    public static final String PROJECT_DUMB_POST_STARTUP = "project dumb post-startup";
    public static final String RUN_PROJECT_POST_STARTUP_ACTIVITIES_DUMB_AWARE = "project post-startup dumb-aware activities";
    public static final String RUN_PROJECT_POST_STARTUP_ACTIVITIES_EDT = "project post-startup edt activities";

    public static final String LOAD_MODULES = "module loading";
    public static final String PROJECT_OPENED_CALLBACKS = "project opened callbacks";

    public static final String RESTORING_EDITORS = "restoring editors";
  }

  @SuppressWarnings("StaticNonFinalField")
  public static boolean measuringPluginStartupCosts = true;

  public static void stopPluginCostMeasurement() {
    measuringPluginStartupCosts = false;
  }

  // ExtensionAreas not available for ExtensionPointImpl
  public enum Level {
    APPLICATION("app"), PROJECT("project"), MODULE("module");

    private final String jsonFieldNamePrefix;

    Level(@NotNull String jsonFieldNamePrefix) {
      this.jsonFieldNamePrefix = jsonFieldNamePrefix;
    }

    @NotNull
    public String getJsonFieldNamePrefix() {
      return jsonFieldNamePrefix;
    }
  }

  private static final long classInitStartTime = System.nanoTime();

  private static final ConcurrentLinkedQueue<ActivityImpl> items = new ConcurrentLinkedQueue<>();

  private static boolean isEnabled = true;

  public static boolean isEnabled() {
    return isEnabled;
  }

  @ApiStatus.Internal
  public static final Map<String, ObjectLongHashMap<String>> pluginCostMap = new HashMap<>();

  public static long getCurrentTime() {
    return System.nanoTime();
  }

  @NotNull
  public static Activity start(@NotNull String name, @Nullable String description) {
    return new ActivityImpl(name, description, null, null);
  }

  @NotNull
  public static Activity start(@NotNull String name) {
    return new ActivityImpl(name, null, null, null);
  }

  @NotNull
  public static Activity start(@NotNull String name, @NotNull Level level) {
    return new ActivityImpl(name, null, level, null);
  }

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
  public static long getClassInitStartTime() {
    return classInitStartTime;
  }

  static void add(@NotNull ActivityImpl activity) {
    if (isEnabled) {
      items.add(activity);
    }
  }

  public static void addTimings(@NotNull LinkedHashMap<String, Long> timings, @NotNull String groupName) {
    if (timings.isEmpty()) {
      return;
    }

    List<Map.Entry<String, Long>> entries = new ArrayList<>(timings.entrySet());

    ActivityImpl parent = new ActivityImpl(groupName, null, entries.get(0).getValue(), null, Level.APPLICATION, null, null);
    parent.setEnd(getCurrentTime());

    for (int i = 0; i < entries.size(); i++) {
      ActivityImpl activity = new ActivityImpl(entries.get(i).getKey(), null, entries.get(i).getValue(), parent, Level.APPLICATION, null, null);
      activity.setEnd(i == entries.size() - 1 ? parent.getEnd() : entries.get(i + 1).getValue());
      items.add(activity);
    }
    items.add(parent);
  }

  public static void addPluginCost(@Nullable String pluginId, @NotNull String phase, long timeNanos) {
    if (pluginId == null || !measuringPluginStartupCosts) {
      return;
    }

    synchronized (pluginCostMap) {
      doAddPluginCost(pluginId, phase, timeNanos, pluginCostMap);
    }
  }

  @ApiStatus.Internal
  public static void doAddPluginCost(@NotNull String pluginId, @NotNull String phase, long timeNanos, @NotNull Map<String, ObjectLongHashMap<String>> pluginCostMap) {
    ObjectLongHashMap<String> costPerPhaseMap = pluginCostMap.get(pluginId);
    if (costPerPhaseMap == null) {
      costPerPhaseMap = new ObjectLongHashMap<>();
      pluginCostMap.put(pluginId, costPerPhaseMap);
    }
    long oldCost = costPerPhaseMap.get(phase);
    if (oldCost == -1) {
      oldCost = 0L;
    }
    costPerPhaseMap.put(phase, oldCost + timeNanos);
  }
}