// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * Utility class for running an external process and capturing its standard output and error streams.
 */
public class CapturingProcessHandler extends OSProcessHandler {
  private final CapturingProcessRunner myProcessRunner;

  public CapturingProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    super(commandLine);
    myProcessRunner = new CapturingProcessRunner(this, processOutput -> createProcessAdapter(processOutput));
  }

  /**
   * {@code commandLine} must not be empty (for correct thread attribution in the stacktrace)
   */
  public CapturingProcessHandler(@NotNull Process process, @Nullable Charset charset, /*@NotNull*/ String commandLine) {
    super(process, commandLine, charset);
    myProcessRunner = new CapturingProcessRunner(this, processOutput -> createProcessAdapter(processOutput));
  }

  protected CapturingProcessAdapter createProcessAdapter(ProcessOutput processOutput) {
    return new CapturingProcessAdapter(processOutput);
  }

  @Override
  public Charset getCharset() {
    return myCharset != null ? myCharset : super.getCharset();
  }

  /**
   * Blocks until process finished, returns its output
   */
  @NotNull
  @RequiresBackgroundThread
  public final ProcessOutput runProcess() {
    return myProcessRunner.runProcess();
  }

  /**
   * Starts process with specified timeout
   *
   * @param timeoutInMilliseconds non-positive means infinity
   */
  @RequiresBackgroundThread
  public ProcessOutput runProcess(int timeoutInMilliseconds) {
    return myProcessRunner.runProcess(timeoutInMilliseconds);
  }

  /**
   * Starts process with specified timeout
   *
   * @param timeoutInMilliseconds non-positive means infinity
   * @param destroyOnTimeout whether to kill the process after timeout passes
   */
  public ProcessOutput runProcess(int timeoutInMilliseconds, boolean destroyOnTimeout) {
    return myProcessRunner.runProcess(timeoutInMilliseconds, destroyOnTimeout);
  }

  @NotNull
  public ProcessOutput runProcessWithProgressIndicator(@NotNull ProgressIndicator indicator) {
    return myProcessRunner.runProcess(indicator);
  }

  @NotNull
  public ProcessOutput runProcessWithProgressIndicator(@NotNull ProgressIndicator indicator, int timeoutInMilliseconds) {
    return myProcessRunner.runProcess(indicator, timeoutInMilliseconds);
  }

  @NotNull
  public ProcessOutput runProcessWithProgressIndicator(@NotNull ProgressIndicator indicator, int timeoutInMilliseconds, boolean destroyOnTimeout) {
    return myProcessRunner.runProcess(indicator, timeoutInMilliseconds, destroyOnTimeout);
  }

  public static class Silent extends CapturingProcessHandler {
    public Silent(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
      super(commandLine);
    }

    public Silent(@NotNull Process process, @Nullable Charset charset, @NotNull String commandLine) {
      super(process, charset, commandLine);
    }

    @NotNull
    @Override
    protected BaseOutputReader.Options readerOptions() {
      return BaseOutputReader.Options.forMostlySilentProcess();
    }
  }
}
