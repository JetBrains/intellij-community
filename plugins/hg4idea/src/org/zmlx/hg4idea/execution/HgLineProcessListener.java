// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.execution;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public abstract class HgLineProcessListener {
  private final @NotNull StringBuilder myErrorOutput = new StringBuilder();
  private int myExitCode;
  private byte @NotNull [] myBinaryOutput = new byte[0];

  public void onLineAvailable(String line, Key outputType) {
    if (ProcessOutputTypes.STDOUT == outputType) {
      processOutputLine(line);
    }
    else if (ProcessOutputTypes.STDERR == outputType) {
      processErrorLine(line);
    }
  }

  protected void processErrorLine(@NotNull String line) {
    myErrorOutput.append(line).append("\n");
  }

  protected abstract void processOutputLine(@NotNull String line);

  public @NotNull StringBuilder getErrorOutput() {
    return myErrorOutput;
  }

  public void finish() throws VcsException {
    if (myExitCode != 0 && !myErrorOutput.isEmpty()) {
      @NlsSafe String message = myErrorOutput.toString();
      throw new VcsException(message);
    }
  }

  public void setExitCode(int exitCode) {
    myExitCode = exitCode;
  }

  public void setBinaryOutput(byte @NotNull [] output) {
    myBinaryOutput = output;
  }

  public byte @NotNull [] getBinaryOutput() {
    return myBinaryOutput;
  }
}
