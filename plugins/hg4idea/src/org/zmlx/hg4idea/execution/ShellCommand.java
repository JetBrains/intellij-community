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
  public HgCommandResult execute(boolean showTextOnIndicator, boolean isBinary) throws ShellCommandException {
    CommandResultCollector listener = new CommandResultCollector(isBinary);
    execute(showTextOnIndicator, isBinary, listener);
    return listener.getResult();
  }

  public void execute(boolean showTextOnIndicator, boolean isBinary, @NotNull HgLineProcessListener listener)
    throws ShellCommandException {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    try {
      OSProcessHandler processHandler = isBinary ? new BinaryOSProcessHandler(myCommandLine) : new OSProcessHandler(myCommandLine);
      ProcessAdapter outputAdapter = new ProcessAdapter() {
        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
          for (String line : LineHandlerHelper.splitText(event.getText())) {
            if (ProcessOutputTypes.STDOUT == outputType && indicator != null && showTextOnIndicator) {
              indicator.setText2(line);
            }
            listener.onLineAvailable(line, outputType);
          }
        }

        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
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
      if (isBinary) {
        listener.setBinaryOutput(((BinaryOSProcessHandler)processHandler).getOutput());
      }
    }
    catch (ExecutionException e) {
      throw new ShellCommandException(e);
    }
  }

  public static class CommandResultCollector extends HgLineProcessListener {
    @NotNull private final ProcessOutput myOutput;
    private final boolean myIsBinary;

    public CommandResultCollector(boolean binary) {
      myIsBinary = binary;
      myOutput = new ProcessOutput();
    }

    @Override
    protected void processOutputLine(@NotNull String line) {
      myOutput.appendStdout(line);
    }

    @Override
    protected void processErrorLine(@NotNull String line) {
      super.processErrorLine(line);
      myOutput.appendStderr(line);
    }

    @Override
    public void setExitCode(int exitCode) {
      super.setExitCode(exitCode);
      myOutput.setExitCode(exitCode);
    }

    public HgCommandResult getResult() {
      return myIsBinary ? new HgCommandResult(myOutput, getBinaryOutput()) : new HgCommandResult(myOutput);
    }
  }
}