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

import com.intellij.execution.testframework.sm.SMTestsRunnerBundle;
import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.messages.serviceMessages.TestIgnored;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestIgnoredEvent extends TreeNodeEvent {
  private final String myIgnoreComment;
  private final String myStacktrace;

  public TestIgnoredEvent(@NotNull String testName, @NotNull String ignoreComment, @Nullable String stacktrace) {
    this(testName, null, ignoreComment, stacktrace);
  }

  public TestIgnoredEvent(@NotNull TestIgnored testIgnored, @Nullable String stacktrace) {
    this(testIgnored.getTestName(), TreeNodeEvent.getNodeId(testIgnored), testIgnored.getIgnoreComment(), stacktrace);
  }

  public TestIgnoredEvent(@Nullable String name, @Nullable String id, @Nullable String ignoreComment, @Nullable String stacktrace) {
    super(name, id);
    myIgnoreComment = ignoreComment;
    myStacktrace = stacktrace;
  }

  @NotNull
  public String getIgnoreComment() {
    if (StringUtil.isEmpty(myIgnoreComment)) {
      return SMTestsRunnerBundle.message("sm.test.runner.states.test.is.ignored");
    }
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
