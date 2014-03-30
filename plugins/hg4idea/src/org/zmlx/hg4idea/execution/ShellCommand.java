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
import com.intellij.execution.process.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.LineHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;

public final class ShellCommand {

  private final GeneralCommandLine myCommandLine;
  private int myExitCode;

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
  public HgCommandResult execute(final boolean showTextOnIndicator) throws ShellCommandException, InterruptedException {
    final StringWriter out = new StringWriter();
    final StringWriter err = new StringWriter();
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    try {
      final Process process = myCommandLine.createProcess();
      final OSProcessHandler processHandler = new OSProcessHandler(process, myCommandLine.toString(), myCommandLine.getCharset());

      processHandler.addProcessListener(new ProcessListener() {
        public void startNotified(final ProcessEvent event) {
        }

        public void processTerminated(final ProcessEvent event) {
          myExitCode = event.getExitCode();
        }

        @Override
        public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
        }

        @Override
        public void onTextAvailable(ProcessEvent event, Key outputType) {
          Iterator<String> lines = LineHandlerHelper.splitText(event.getText()).iterator();
          if (ProcessOutputTypes.STDOUT == outputType) {
            while (lines.hasNext()) {
              String line = lines.next();
              if (indicator != null && showTextOnIndicator) {
                indicator.setText2(line);
              }
              out.write(line);
            }
          }
          else if (ProcessOutputTypes.STDERR == outputType) {
            while (lines.hasNext()) {
              err.write(lines.next());
            }
          }
        }
      });

      processHandler.startNotify();
      while (!processHandler.waitFor(300)) {
        if (indicator != null && indicator.isCanceled()) {
          processHandler.destroyProcess();
          myExitCode = 255;
          break;
        }
      }
      return new HgCommandResult(out, err, myExitCode);
    }
    catch (ExecutionException e) {
      throw new ShellCommandException(e);
    }
  }
}
