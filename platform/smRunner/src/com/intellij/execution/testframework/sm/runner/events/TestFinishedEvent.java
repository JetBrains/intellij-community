// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.events;

import jetbrains.buildServer.messages.serviceMessages.TestFinished;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestFinishedEvent extends TreeNodeEvent {
  private final @Nullable Long myDuration;
  private final String myOutputFile;

  public TestFinishedEvent(@NotNull TestFinished testFinished, @Nullable Long duration) {
    this(testFinished, duration, null);
  }

  public TestFinishedEvent(@NotNull TestFinished testFinished, @Nullable Long duration, String outputFile) {
    this(testFinished.getTestName(), TreeNodeEvent.getNodeId(testFinished), duration, outputFile);
  }

  public TestFinishedEvent(@Nullable String name, @Nullable String id, @Nullable Long duration) {
    this(name, id, duration, null);
  }

  public TestFinishedEvent(@Nullable String name, @Nullable String id, @Nullable Long duration, String outputFile) {
    super(name, id);
    myDuration = duration;
    myOutputFile = outputFile;
  }

  public TestFinishedEvent(@NotNull String name, @Nullable Long duration) {
    this(name, null, duration);
  }

  /**
   * @return duration in ms if reported
   */
  public @Nullable Long getDuration() {
    return myDuration;
  }

  @Override
  protected void appendToStringInfo(@NotNull StringBuilder buf) {
    append(buf, "duration", myDuration);
  }

  public String getOutputFile() {
    return myOutputFile;
  }
}
