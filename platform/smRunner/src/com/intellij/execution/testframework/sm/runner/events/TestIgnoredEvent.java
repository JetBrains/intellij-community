/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import jetbrains.buildServer.messages.serviceMessages.TestIgnored;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey Simonchik
 */
public class TestIgnoredEvent extends TreeNodeEvent {
  private final String myIgnoreComment;
  private final String myStacktrace;

  public TestIgnoredEvent(@NotNull String testName, @NotNull String ignoreComment, @Nullable String stacktrace) {
    super(testName, -1);
    myIgnoreComment = ignoreComment;
    myStacktrace = stacktrace;
  }

  public TestIgnoredEvent(@NotNull TestIgnored testIgnored, @Nullable String stacktrace) {
    super(testIgnored.getTestName(), TreeNodeEvent.getNodeId(testIgnored));
    myIgnoreComment = testIgnored.getIgnoreComment();
    myStacktrace = stacktrace;
  }

  @Nullable
  public String getIgnoreComment() {
    return myIgnoreComment;
  }

  @Nullable
  public String getStacktrace() {
    return myStacktrace;
  }

  @Override
  protected void appendToStringInfo(@NotNull StringBuilder buf) {
    append(buf, "ignoreComment", myIgnoreComment);
    append(buf, "stacktrace", myStacktrace);
  }
}
