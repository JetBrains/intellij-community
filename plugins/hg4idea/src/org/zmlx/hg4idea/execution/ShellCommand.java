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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.List;

public final class ShellCommand {

  private final GeneralCommandLine myCommandLine;

  public ShellCommand(@Nullable List<String> commandLine, @Nullable String dir, @Nullable Charset charset) {
    if (commandLine == null || commandLine.isEmpty()) {
      throw new IllegalArgumentException("commandLine is empty");
    }
    myCommandLine = new GeneralCommandLine(commandLine);
    if (dir != null) {
      myCommandLine.setWorkDirectory(new File(dir));
    }
    if (charset != null) {
      myCommandLine.setCharset(charset);
    }
  }

  @NotNull
  public HgCommandResult execute() throws ShellCommandException, InterruptedException {
    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();
    try {
      Process process = myCommandLine.createProcess();

      CapturingProcessHandler processHandler = new CapturingProcessHandler(process, myCommandLine.getCharset());
      final ProcessOutput processOutput = processHandler.runProcess();

      int exitValue = processOutput.getExitCode();
      out.write(processOutput.getStdout());
      err.write(processOutput.getStderr());
      return new HgCommandResult(out, err, exitValue);
    }
    catch (ExecutionException e) {
      throw new ShellCommandException(e);
    }
  }
}
