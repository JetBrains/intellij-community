/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.testframework.sm.runner.events;

import jetbrains.buildServer.messages.serviceMessages.TestFinished;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestFinishedEvent extends TreeNodeEvent {

  private final long myDuration;
  private final String myOutputFile;

  public TestFinishedEvent(@NotNull TestFinished testFinished, long duration) {
    this(testFinished, duration, null);
  }

  public TestFinishedEvent(@NotNull TestFinished testFinished, long duration, String outputFile) {
    this(testFinished.getTestName(), TreeNodeEvent.getNodeId(testFinished), duration, outputFile);
  }

  public TestFinishedEvent(@Nullable String name, int id, long duration) {
    this(name, id, duration, null);
  }

  public TestFinishedEvent(@Nullable String name, int id, long duration, String outputFile) {
    super(name, id);
    myDuration = duration;
    myOutputFile = outputFile;
  }

  public TestFinishedEvent(@NotNull String name, long duration) {
    this(name, -1, duration);
  }

  /** @deprecated use {@link #TestFinishedEvent(String, long)} (to be removed in IDEA 16) */
  @SuppressWarnings("unused")
  public TestFinishedEvent(@NotNull String name, int duration) {
    this(name, -1, duration);
  }

  public long getDuration() {
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
