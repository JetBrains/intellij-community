// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class StartUpMeasurer {
  public static final long MEASURE_THRESHOLD = TimeUnit.MILLISECONDS.toNanos(10);

  // Use constants for better overview of existing phases (and preserve consistent naming).
  // `what + noun` is used as scheme for name to make analyzing easier (to visually group - `components loading/initialization/etc`,
  // not to put common part of name to end of).
  // It is not serves only display purposes - it is IDs. Visualizer and another tools to analyze data uses phase IDs,
  // so, any changes must be discussed across all involved and reflected in changelog (see `format-changelog.md`).
  public static final class Phases {
    // this phase name is not fully clear - it is time from `PluginManager.start` to `IdeaApplication.initApplication`
    public static final String PREPARE_TO_INIT_APP = "app initialization preparation";

    public static final String RUN_PREPARE_APP_INIT_ACTIVITIES = "prepare app init activities";
    public static final String WAIT_PARALLEL_PREPARE_APP = "wait parallel prepare";

    public static final String LOAD_MAIN_CLASS = "load main class";
    public static final String CONFIGURE_LOGGING = "configure logging";
    public static final String INIT_DEFAULT_LAF = "init default LaF";
    public static final String UPDATE_FRAME_CLASS = "update frame class";
    public static final String UPDATE_WINDOW_ICON = "update window icon";
    public static final String REGISTER_BUNDLED_FONTS = "register bundled fonts";

    // this phase name is not fully clear - it is time from `IdeaApplication.initApplication` to `IdeaApplication.run`
    public static final String INIT_APP = "app initialization";

    public static final String LOAD_PLUGIN_DESCRIPTORS = "plugin descriptors loading";
    public static final String INIT_PLUGINS = "plugin initialization";

    public static final String INITIALIZE_COMPONENTS_SUFFIX = "component initialization";
    // actually, now it is also registers services, not only components,but it doesn't worth to rename
    public static final String REGISTER_COMPONENTS_SUFFIX = "component registration";
    public static final String COMPONENTS_REGISTERED_CALLBACK_SUFFIX = "component registered callback";
    public static final String CREATE_COMPONENTS_SUFFIX = "component creation";

    public static final String APP_INITIALIZED_CALLBACK = "app initialized callback";

    public static final String PROJECT_PRE_STARTUP = "project pre-startup";
    public static final String PROJECT_STARTUP = "project startup";

    public static final String PROJECT_DUMB_POST_STARTUP = "project dumb post-startup";

    public static final String LOAD_MODULES = "module loading";
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

  // non-sequential and repeated items
  public static final class Activities {
    public static final String COMPONENT = "component";
    public static final String SERVICE = "service";
    public static final String EXTENSION = "extension";
  }

  public static final class ActivitySubNames {
    public static final String CHECK_SYSTEM_DIR = "check system dirs";
    public static final String LOCK_SYSTEM_DIRS = "lock system dirs";
    public static final String START_LOGGING = "start logging";
    public static final String LOAD_SYSTEM_LIBS = "load system libs";
    public static final String FIX_PROCESS_ENV = "fix process env";
  }

  private static final long classInitStartTime = System.nanoTime();

  private static final ConcurrentLinkedQueue<ActivityImpl> items = new ConcurrentLinkedQueue<>();

  public static long getCurrentTime() {
    return System.nanoTime();
  }

  @NotNull
  public static Activity start(@NotNull String name, @Nullable String description) {
    return new ActivityImpl(name, description, null);
  }

  @NotNull
  public static Activity start(@NotNull String name) {
    return new ActivityImpl(name, null, null);
  }

  @NotNull
  public static Activity start(@NotNull String name, @NotNull Level level) {
    return new ActivityImpl(name, null, level);
  }

  public static void processAndClear(@NotNull Consumer<ActivityImpl> consumer) {
    while (true) {
      ActivityImpl item = items.poll();
      if (item == null) {
        break;
      }

      consumer.accept(item);
    }
  }

  /**
   * Internal use only.
   */
  public static long getClassInitStartTime() {
    return classInitStartTime;
  }

  static void add(@NotNull ActivityImpl activity) {
    items.add(activity);
  }
}