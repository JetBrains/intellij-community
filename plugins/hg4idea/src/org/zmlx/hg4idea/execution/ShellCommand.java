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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.LineHandlerHelper;
import com.intellij.vcs.VcsLocaleHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;

public final class ShellCommand {
  private final GeneralCommandLine myCommandLine;

  public ShellCommand(@NotNull List<String> commandLine, @Nullable String dir, @Nullable Charset charset) {
    if (commandLine.isEmpty()) {
      throw new IllegalArgumentException("commandLine is empty");
    }
    myCommandLine = new GeneralCommandLine(commandLine);
    if (dir != null) {
      myCommandLine.setWorkDirectory(new File(dir));
    }
    if (charset != null) {
      myCommandLine.setCharset(charset);
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      //ignore all hg config files except current repository config
      myCommandLine.getEnvironment().put("HGRCPATH", "");
    }
    myCommandLine.withEnvironment(VcsLocaleHelper.getDefaultLocaleEnvironmentVars("hg"));
  }

  @NotNull
  public HgCommandResult execute(final boolean showTextOnIndicator, boolean isBinary) throws ShellCommandException, InterruptedException {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    try {
      OSProcessHandler processHandler = isBinary ? new BinaryOSProcessHandler(myCommandLine) : new OSProcessHandler(myCommandLine);
      CapturingProcessAdapter outputAdapter = new CapturingProcessAdapter() {

        @Override
        public void onTextAvailable(ProcessEvent event, Key outputType) {
          Iterator<String> lines = LineHandlerHelper.splitText(event.getText()).iterator();
          if (ProcessOutputTypes.STDOUT == outputType) {
            while (lines.hasNext()) {
              String line = lines.next();
              if (indicator != null && showTextOnIndicator) {
                indicator.setText2(line);
              }
              addToOutput(line, ProcessOutputTypes.STDOUT);
            }
          }
          else {
            super.onTextAvailable(event, outputType);
          }
        }
      };
      processHandler.addProcessListener(outputAdapter);
      processHandler.startNotify();
      while (!processHandler.waitFor(300)) {
        if (indicator != null && indicator.isCanceled()) {
          processHandler.destroyProcess();
          outputAdapter.getOutput().setExitCode(255);
          break;
        }
      }
      ProcessOutput output = outputAdapter.getOutput();
      return isBinary ? new HgCommandResult(output, ((BinaryOSProcessHandler)processHandler).getOutput()) : new HgCommandResult(output);
    }
    catch (ExecutionException e) {
      throw new ShellCommandException(e);
    }
  }

  public void execute(boolean showTextOnIndicator, @NotNull HgLineProcessListener listener)
    throws ShellCommandException, InterruptedException {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    try {
      OSProcessHandler processHandler = new OSProcessHandler(myCommandLine);
      ProcessAdapter outputAdapter = new ProcessAdapter() {
        @Override
        public void onTextAvailable(ProcessEvent event, Key outputType) {
          for (String line : LineHandlerHelper.splitText(event.getText())) {
            if (ProcessOutputTypes.STDOUT == outputType && indicator != null && showTextOnIndicator) {
              indicator.setText2(line);
            }
            listener.onLineAvailable(line, outputType);
          }
        }

        @Override
        public void processTerminated(ProcessEvent event) {
          listener.setExitCode(event.getExitCode());
        }
      };
      processHandler.addProcessListener(outputAdapter);
      processHandler.startNotify();
      while (!processHandler.waitFor(300)) {
        if (indicator != null && indicator.isCanceled()) {
          processHandler.destroyProcess();
          listener.setExitCode(255);
          break;
        }
      }
    }
    catch (ExecutionException e) {
      throw new ShellCommandException(e);
    }
  }
}