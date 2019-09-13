// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

// use only JDK classes here (avoid StringUtil and so on)
public final class ActivityImpl implements Activity {
  private final String name;
  private String description;

  private final String threadName;
  private final long threadId;

  private final long start;
  private long end;

  // null doesn't mean root - not obligated to set parent, only as hint
  private final ActivityImpl parent;

  @Nullable
  private final StartUpMeasurer.Level level;

  @Nullable
  private ActivityCategory parallelActivity;
  @Nullable
  private final String pluginId;

  ActivityImpl(@Nullable String name, @Nullable StartUpMeasurer.Level level, @Nullable String pluginId) {
    this(name, System.nanoTime(), null, level, pluginId);
  }

  ActivityImpl(@Nullable String name,
               long start,
               @Nullable ActivityImpl parent,
               @Nullable StartUpMeasurer.Level level,
               @Nullable String pluginId) {
    this.name = name;
    this.start = start;
    this.parent = parent;
    this.level = level;
    this.pluginId = pluginId;

    Thread thread = Thread.currentThread();
    threadId = thread.getId();
    threadName = thread.getName();
  }

  @NotNull
  public String getThreadName() {
    return threadName;
  }

  public long getThreadId() {
    return threadId;
  }

  @Nullable
  public ActivityImpl getParent() {
    return parent;
  }

  @Nullable
  public StartUpMeasurer.Level getLevel() {
    return level;
  }

  @Nullable
  public ActivityCategory getParallelActivity() {
    return parallelActivity;
  }

  void setCategory(@Nullable ActivityCategory value) {
    parallelActivity = value;
  }

  // and how do we can sort correctly, when parent item equals to child (start and end), also there is another child with start equals to end?
  // so, parent added to API but as it was not enough, decided to measure time in nanoseconds instead of ms to mitigate such situations
  @Override
  @NotNull
  public ActivityImpl startChild(@NotNull String name) {
    return new ActivityImpl(name, System.nanoTime(), this, null, pluginId);
  }

  @NotNull
  public String getName() {
    return name;
  }

  @Nullable
  public String getDescription() {
    return description;
  }

  void setDescription(String value) {
    description = value;
  }

  @Nullable
  public String getPluginId() {
    return pluginId;
  }

  public long getStart() {
    return start;
  }

  public long getEnd() {
    return end;
  }

  void setEnd(long end) {
    assert this.end == 0;
    this.end = end;
  }

  @Override
  public void end(@Nullable String description) {
    if (description != null) {
      this.description = description;
    }
    assert end == 0 : "not started or already ended";
    end = StartUpMeasurer.getCurrentTime();
    StartUpMeasurer.add(this);
  }

  @Override
  @NotNull
  public Activity endAndStart(@NotNull String name) {
    end();
    ActivityImpl activity = new ActivityImpl(name, /* start = */end, parent, /* level = */null, pluginId);
    activity.setCategory(parallelActivity);
    return activity;
  }

  @Override
  public String toString() {
    return "ActivityImpl(name=" + name + ", start=" + TimeUnit.NANOSECONDS.toMillis(start) + ", end=" + TimeUnit.NANOSECONDS.toMillis(end) + ")";
  }
}