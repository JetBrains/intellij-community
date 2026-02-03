// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.intellij.openapi.util.NlsSafe;

import java.util.EventObject;

public final class ProcessEvent extends EventObject{
  private @NlsSafe String myText;
  private int myExitCode;

  public ProcessEvent(final ProcessHandler source) {
    super(source);
  }

  public ProcessEvent(final ProcessHandler source, final @NlsSafe String text) {
    super(source);
    myText = text;
  }

  public ProcessEvent(final ProcessHandler source, final int exitCode) {
    super(source);
    myExitCode = exitCode;
  }

  public ProcessHandler getProcessHandler() {
    return (ProcessHandler)getSource();
  }

  public @NlsSafe String getText() {
    return myText;
  }

  public int getExitCode() {
    return myExitCode;
  }
}