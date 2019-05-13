// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.execution;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class HgCommandResult {

  //should be deleted and use ProcessOutput without wrapper

  public static final HgCommandResult CANCELLED = new HgCommandResult(new ProcessOutput(1));

  @NotNull private final ProcessOutput myProcessOutput;
  @NotNull private final byte[] myByteArrayOutput;

  public HgCommandResult(@NotNull ProcessOutput processOutput) {
    this(processOutput, ArrayUtil.EMPTY_BYTE_ARRAY);
  }

  public HgCommandResult(@NotNull ProcessOutput processOutput, @NotNull byte[] byteArrayOutput) {
    myProcessOutput = processOutput;
    myByteArrayOutput = byteArrayOutput;
  }

  @NotNull
  public List<String> getOutputLines() {
    return myProcessOutput.getStdoutLines();
  }

  @NotNull
  public List<String> getErrorLines() {
    return myProcessOutput.getStderrLines();
  }

  @NotNull
  public String getRawOutput() {
    return myProcessOutput.getStdout();
  }

  @NotNull
  public String getRawError() {
    return myProcessOutput.getStderr();
  }

  @NotNull
  public byte[] getBytesOutput() {
    return myByteArrayOutput;
  }

  public int getExitValue() {
    return myProcessOutput.getExitCode();
  }
}