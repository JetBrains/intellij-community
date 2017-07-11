/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.zmlx.hg4idea.execution;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public abstract class HgLineProcessListener {
  @NotNull private final StringBuilder myErrorOutput = new StringBuilder();
  private int myExitCode;

  public void onLineAvailable(String line, Key outputType) {
    if (ProcessOutputTypes.STDOUT == outputType) {
      processOutputLine(line);
    }
    else if (ProcessOutputTypes.STDERR == outputType) {
      myErrorOutput.append(line).append("\n");
    }
  }

  protected abstract void processOutputLine(@NotNull String line);

  @NotNull
  public StringBuilder getErrorOutput() {
    return myErrorOutput;
  }

  public void finish() throws VcsException {
    if (myExitCode != 0 && myErrorOutput.length() != 0) {
      throw new VcsException(myErrorOutput.toString());
    }
  }

  public void setExitCode(int exitCode) {
    myExitCode = exitCode;
  }
}
