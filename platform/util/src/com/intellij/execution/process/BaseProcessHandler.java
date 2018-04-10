/*
 * Copyright 2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.process;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.TaskExecutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
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
  protected final String myPresentableName;
  protected final ProcessWaitFor myWaitFor;

  /**
   * {@code commandLine} must not be not empty (for correct thread attribution in the stacktrace)
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
  public T getProcess() {
    return myProcess;
  }

  /*@NotNull*/
  public String getCommandLine() {
    return myCommandLine;
  }

  @Nullable
  public Charset getCharset() {
    return myCharset;
  }

  @Override
  public OutputStream getProcessInput() {
    return myProcess.getOutputStream();
  }

  protected void onOSProcessTerminated(final int exitCode) {
    notifyProcessTerminated(exitCode);
  }

  protected void doDestroyProcess() {
    getProcess().destroy();
  }

  @Override
  protected void destroyProcessImpl() {
    try {
      closeStreams();
    }
    finally {
      doDestroyProcess();
    }
  }

  @Override
  protected void detachProcessImpl() {
    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        closeStreams();

        myWaitFor.detach();
        notifyProcessDetached();
      }
    };

    executeTask(runnable);
  }

  @Override
  public boolean detachIsDefault() {
    return false;
  }

  protected void closeStreams() {
    try {
      myProcess.getOutputStream().close();
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }
}