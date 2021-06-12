// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetedCommandLine;
import com.intellij.lang.ant.segments.Extractor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;

public final class AntProcessHandler extends KillableColoredProcessHandler {
  private final Extractor myOut;
  private final Extractor myErr;

  private AntProcessHandler(@NotNull TargetedCommandLine commandLine,
                            @NotNull TargetEnvironment targetEnvironment,
                            @NotNull ProgressIndicator progressIndicator) throws ExecutionException {
    super(targetEnvironment.createProcess(commandLine, progressIndicator), commandLine.getCommandPresentation(targetEnvironment));

    myOut = new Extractor(getProcess().getInputStream(), commandLine.getCharset());
    myErr = new Extractor(getProcess().getErrorStream(), commandLine.getCharset());
    addProcessListener(new ProcessAdapter(){
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        Disposer.dispose(myOut);
        Disposer.dispose(myErr);
      }
    });
  }

  @NotNull
  @Override
  protected Reader createProcessOutReader() {
    return myOut.createReader();
  }

  @NotNull
  @Override
  protected Reader createProcessErrReader() {
    return myErr.createReader();
  }

  @NotNull
  public Extractor getErr() {
    return myErr;
  }

  @NotNull
  public Extractor getOut() {
    return myOut;
  }

  @NotNull
  public static AntProcessHandler runCommandLine(@NotNull TargetedCommandLine commandLine,
                                                 @NotNull TargetEnvironment environment,
                                                 @NotNull ProgressIndicator progressIndicator) throws ExecutionException {
    final AntProcessHandler processHandler = new AntProcessHandler(commandLine, environment, progressIndicator);
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }

  @NotNull
  @Override
  protected BaseOutputReader.Options readerOptions() {
    return BaseOutputReader.Options.NON_BLOCKING;
  }
}