// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.events;

import jetbrains.buildServer.messages.serviceMessages.TestSuiteFinished;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestSuiteFinishedEvent extends TreeNodeEvent {
  private final @Nullable Long myDuration;

  public TestSuiteFinishedEvent(@NotNull TestSuiteFinished suiteFinished, @Nullable String duration) {
    this(suiteFinished.getSuiteName(), getNodeId(suiteFinished), toDuration(parseDuration(duration)));
  }

  public TestSuiteFinishedEvent(@NotNull TestSuiteFinished suiteFinished, @Nullable Long duration) {
    this(suiteFinished.getSuiteName(), getNodeId(suiteFinished), duration);
  }

  public TestSuiteFinishedEvent(@NotNull String name) {
    this(name, null, null);
  }

  public TestSuiteFinishedEvent(@Nullable String name, @Nullable String id) {
    this(name, id, null);
  }

  public TestSuiteFinishedEvent(@Nullable String name, @Nullable String id, @Nullable Long duration) {
    super(name, id);
    myDuration = duration;
  }

  public @Nullable Long getDuration() {
    return myDuration;
  }

  @Override
  protected void appendToStringInfo(@NotNull StringBuilder buf) {
    if (myDuration != null && myDuration >= 0) append(buf, "duration", myDuration);
  }

  private static @Nullable Long toDuration(long duration) {
    return duration >= 0 ? duration : null;
  }
}
