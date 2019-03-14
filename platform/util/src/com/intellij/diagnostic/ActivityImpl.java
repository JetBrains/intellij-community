// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ActivityImpl implements Activity {
  private final String name;
  private String description;

  private final long start;
  private long end;

  // null doesn't mean root - not obligated to set parent, only as hint
  private final ActivityImpl parent;

  @Nullable
  private final StartUpMeasurer.Level level;

  @Nullable
  private final ParallelActivity parallelActivity;

  ActivityImpl(@Nullable String name, @Nullable String description, @Nullable StartUpMeasurer.Level level) {
    this(name, description, System.nanoTime(), null, level, null);
  }

  @NotNull
  static ActivityImpl createParallelActivity(@NotNull ParallelActivity parallelActivity, @NotNull String name) {
    return new ActivityImpl(name, /* description = */ null, System.nanoTime(), /* parent = */ null, /* level = */ null, parallelActivity);
  }

  ActivityImpl(@Nullable String name,
               @Nullable String description,
               long start,
               @Nullable ActivityImpl parent,
               @Nullable StartUpMeasurer.Level level,
               @Nullable ParallelActivity parallelActivity) {
    this.name = name;
    this.description = StringUtil.nullize(description);
    this.start = start;
    this.parent = parent;
    this.level = level;
    this.parallelActivity = parallelActivity;
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
  public ParallelActivity getParallelActivity() {
    return parallelActivity;
  }

  // and how do we can sort correctly, when parent item equals to child (start and end) and also there is another child with start equals to end?
  // so, parent added to API but as it was not enough, decided to measure time in nanoseconds instead of ms to mitigate such situations
  @Override
  @NotNull
  public ActivityImpl startChild(@NotNull String name) {
    return new ActivityImpl(name, null, System.nanoTime(), this, null, null);
  }

  @NotNull
  public String getName() {
    return name;
  }

  @Nullable
  public String getDescription() {
    return description;
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
    assert end == 0;
    end = System.nanoTime();
    StartUpMeasurer.add(this);
  }

  @Override
  public void endWithThreshold(@NotNull Class<?> clazz) {
    this.description = clazz.getName();
    end = System.nanoTime();
    if ((end - start) > StartUpMeasurer.MEASURE_THRESHOLD) {
      StartUpMeasurer.add(this);
    }
  }

  @Override
  @NotNull
  public Activity endAndStart(@NotNull String name) {
    end();
    return new ActivityImpl(name, /* description = */null, /* start = */end, parent, /* level = */null, parallelActivity);
  }

  @Override
  public String toString() {
    return name;
  }
}
