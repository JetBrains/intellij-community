/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.lang.ant.segments.Extractor;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;

/**
 * @author dyoma
 */
public class AntProcessHandler extends KillableColoredProcessHandler {
  private final Extractor myOut;
  private final Extractor myErr;

  private AntProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    super(commandLine);

    myOut = new Extractor(getProcess().getInputStream(), commandLine.getCharset());
    myErr = new Extractor(getProcess().getErrorStream(), commandLine.getCharset());
    addProcessListener(new ProcessAdapter(){
      @Override
      public void processTerminated(ProcessEvent event) {
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
  public static AntProcessHandler runCommandLine(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    final AntProcessHandler processHandler = new AntProcessHandler(commandLine);
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }

  @NotNull
  @Override
  protected BaseOutputReader.Options readerOptions() {
    return BaseOutputReader.Options.NON_BLOCKING;
  }
}