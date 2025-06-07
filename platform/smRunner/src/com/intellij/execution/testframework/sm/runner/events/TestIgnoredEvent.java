// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.events;

import com.intellij.execution.testframework.sm.SmRunnerBundle;
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

  public @NotNull String getIgnoreComment() {
    if (StringUtil.isEmpty(myIgnoreComment)) {
      return SmRunnerBundle.message("sm.test.runner.states.test.is.ignored");
    }
    return myIgnoreComment;
  }

  public @Nullable String getStacktrace() {
    return myStacktrace;
  }

  @Override
  protected void appendToStringInfo(@NotNull StringBuilder buf) {
    append(buf, "ignoreComment", myIgnoreComment);
    append(buf, "stacktrace", myStacktrace);
  }
}
