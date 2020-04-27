// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public class CapturingProcessAdapter extends ProcessAdapter {
  private final ProcessOutput myOutput;

  public CapturingProcessAdapter() {
    this(new ProcessOutput());
  }

  public CapturingProcessAdapter(@NotNull ProcessOutput output) {
    myOutput = output;
  }

  @Override
  public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
    addToOutput(event.getText(), outputType);
  }

  protected void addToOutput(String text, Key outputType) {
    if (outputType == ProcessOutputTypes.STDOUT) {
      myOutput.appendStdout(text);
    }
    else if (outputType == ProcessOutputTypes.STDERR) {
      myOutput.appendStderr(text);
    }
  }

  @Override
  public void processTerminated(@NotNull ProcessEvent event) {
    myOutput.setExitCode(event.getExitCode());
  }

  public ProcessOutput getOutput() {
    return myOutput;
  }
}
