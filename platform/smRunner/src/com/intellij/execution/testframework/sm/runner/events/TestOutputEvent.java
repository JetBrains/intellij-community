// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner.events;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import jetbrains.buildServer.messages.serviceMessages.BaseTestMessage;
import org.jetbrains.annotations.NotNull;

public class TestOutputEvent extends TreeNodeEvent {
  private final String myText;
  private final Key myOutputType;

  public TestOutputEvent(@NotNull BaseTestMessage message, @NotNull String text, @NotNull Key outputType) {
    super(message.getTestName(), TreeNodeEvent.getNodeId(message));
    myText = text;
    myOutputType = outputType;
  }

  public TestOutputEvent(@NotNull String testName, @NotNull String text, boolean stdOut) {
    super(testName, null);
    myText = text;
    myOutputType = stdOut ? ProcessOutputTypes.STDOUT : ProcessOutputTypes.STDERR;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public Key getOutputType() {
    return myOutputType;
  }

  @Override
  protected void appendToStringInfo(@NotNull StringBuilder buf) {
    append(buf, "text", myText);
    append(buf, "outputType", myOutputType);
  }
}
