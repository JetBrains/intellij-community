// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

// use only JDK classes here (avoid StringUtil and so on)
public final class ActivityImpl implements Activity {
  private final String name;
  private String description;

  private String threadName;
  private long threadId;

  private final long start;
  private long end;

  // null doesn't mean root - not obligated to set parent, only as hint
  private final ActivityImpl parent;

  private @Nullable final ActivityCategory category;

  private final @Nullable String pluginId;

  @SuppressWarnings("StaticNonFinalField")
  @ApiStatus.Internal
  public static volatile Consumer<ActivityImpl> listener;

  ActivityImpl(@Nullable String name, long start, @Nullable ActivityImpl parent) {
    this(name, start, parent, null);
  }

  ActivityImpl(@Nullable String name, long start, @Nullable ActivityImpl parent, @Nullable String pluginId) {
    this(name, start, parent, pluginId, null);
  }

  ActivityImpl(@Nullable String name, long start, @Nullable ActivityImpl parent, @Nullable String pluginId, @Nullable ActivityCategory category) {
    this.name = name;
    this.start = start;
    this.parent = parent;
    this.pluginId = pluginId;
    this.category = category;

    updateThreadName();

    Consumer<ActivityImpl> listener = ActivityImpl.listener;
    if (listener != null) {
      listener.accept(this);
    }
  }

  public @NotNull String getThreadName() {
    return threadName;
  }

  // Not clear - should we always set it on end of activity or not. Method maybe called in a such rare cases.
  @Override
  public void updateThreadName() {
    Thread thread = Thread.currentThread();
    threadId = thread.getId();
    threadName = thread.getName();
  }

  public long getThreadId() {
    return threadId;
  }

  public @Nullable ActivityImpl getParent() {
    return parent;
  }

  public @Nullable ActivityCategory getCategory() {
    return category;
  }

  // and how do we can sort correctly, when parent item equals to child (start and end), also there is another child with start equals to end?
  // so, parent added to API but as it was not enough, decided to measure time in nanoseconds instead of ms to mitigate such situations
  @Override
  public @NotNull ActivityImpl startChild(@NotNull String name) {
    return new ActivityImpl(name, StartUpMeasurer.getCurrentTime(), this, pluginId, category);
  }

  public @NotNull String getName() {
    return name;
  }

  public @Nullable String getDescription() {
    return description;
  }

  public @Nullable String getPluginId() {
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
  public void end() {
    end(StartUpMeasurer.getCurrentTime());
  }

  void end(long time) {
    assert end == 0 : "not started or already ended";
    end = time;
    StartUpMeasurer.addActivity(this);

    Consumer<ActivityImpl> listener = ActivityImpl.listener;
    if (listener != null) {
      listener.accept(this);
    }
  }

  @Override
  public void setDescription(@NotNull String value) {
    description = value;
  }

  @Override
  public @NotNull ActivityImpl endAndStart(@NotNull String name) {
    return endAndStart(StartUpMeasurer.getCurrentTime(), name);
  }

  @NotNull ActivityImpl endAndStart(long time, @NotNull String name) {
    end(time);
    return new ActivityImpl(name, /* start = */end, parent, /* level = */ pluginId, category);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("ActivityImpl(name=").append(name).append(", start=");
    nanoToString(start, builder);
    builder.append(", end=");
    nanoToString(end, builder);
    builder.append(", category=").append(category).append(")");
    return builder.toString();
  }

  private static void nanoToString(long start, @NotNull StringBuilder builder) {
    //noinspection NonAsciiCharacters
    builder
      .append(TimeUnit.NANOSECONDS.toMillis(start - StartUpMeasurer.getStartTime())).append("ms (")
      .append(TimeUnit.NANOSECONDS.toMicros(start - StartUpMeasurer.getStartTime())).append("μs)");
  }
}