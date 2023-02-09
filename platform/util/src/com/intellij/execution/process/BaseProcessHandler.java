// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.TaskExecutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

public abstract class BaseProcessHandler<T extends Process> extends ProcessHandler implements TaskExecutor {
  private static final Logger LOG = Logger.getInstance(BaseProcessHandler.class);

  protected final T myProcess;
  protected final String myCommandLine;
  protected final Charset myCharset;
  protected final @NonNls String myPresentableName;
  protected final ProcessWaitFor myWaitFor;

  /**
   * {@code commandLine} must not be empty (for correct thread attribution in the stacktrace)
   */
  public BaseProcessHandler(@NotNull T process, /*@NotNull*/ String commandLine, @Nullable Charset charset) {
    myProcess = process;
    myCommandLine = commandLine;
    myCharset = charset;
    if (StringUtil.isEmpty(commandLine)) {
      LOG.warn(new IllegalArgumentException("Must specify non-empty 'commandLine' parameter"));
    }
    myPresentableName = CommandLineUtil.extractPresentableName(StringUtil.notNullize(commandLine));
    myWaitFor = new ProcessWaitFor(process, this, myPresentableName);
  }

  @NotNull
  public final T getProcess() {
    return myProcess;
  }

  /*@NotNull*/
  public @NlsSafe String getCommandLine() {
    return myCommandLine;
  }

  @Nullable
  public Charset getCharset() {
    return myCharset;
  }

  @Override
  public @NotNull OutputStream getProcessInput() {
    return myProcess.getOutputStream();
  }

  protected void onOSProcessTerminated(final int exitCode) {
    notifyProcessTerminated(exitCode);
    closeStreams();
  }

  protected void doDestroyProcess() {
    getProcess().destroy();
  }

  @Override
  protected void destroyProcessImpl() {
    doDestroyProcess();
  }

  @Override
  protected void detachProcessImpl() {
    final Runnable runnable = () -> {
      closeStreams();

      myWaitFor.detach();
      notifyProcessDetached();
    };

    executeTask(runnable);
  }

  @Override
  public boolean detachIsDefault() {
    return false;
  }

  private void closeStreams() {
    try {
      myProcess.getOutputStream().close();
    }
    catch (IOException e) {
      // The process may have already terminated, but some data has not yet been written to its standard input.
      // For example, `com.intellij.execution.process.ProcessServiceImpl.sendWinProcessCtrlC(int, OutputStream)`
      // tries to terminate a process with `GenerateConsoleCtrlEvent(CTRL_C_EVENT)` and then writes `-1` to process's input to
      // unblock ReadConsoleW/ReadFile.
      // In this case, `close` will fail, because of close -> flush -> write, and 'write' cannot be performed
      // on a stream of the terminated process.
      if (myProcess.isAlive()) {
        LOG.warn("Cannot close stdin of '" + getCommandLine() + "'", e);
      }
    }
    try {
      myProcess.getInputStream().close();
    }
    catch (IOException e) {
      if (myProcess.isAlive()) {
        LOG.warn("Cannot close stdout of '" + getCommandLine() + "'", e);
      }
    }
    try {
      myProcess.getErrorStream().close();
    }
    catch (IOException e) {
      if (myProcess.isAlive()) {
        LOG.warn("Cannot close stderr of '" + getCommandLine() + "'", e);
      }
    }
  }
}