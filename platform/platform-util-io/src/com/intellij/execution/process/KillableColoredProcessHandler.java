// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.KillableProcess;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Set;

/**
 * This process handler supports both ANSI coloring (see {@link ColoredProcessHandler})
 * and "soft-kill" feature (see {@link KillableProcessHandler}).
 */
public class KillableColoredProcessHandler extends ColoredProcessHandler implements KillableProcess {
  public KillableColoredProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    super(commandLine);
    setShouldKillProcessSoftly(true);
  }

  /** @deprecated the mediator is retired; use {@link #KillableColoredProcessHandler(GeneralCommandLine)} instead */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("unused")
  public KillableColoredProcessHandler(@NotNull GeneralCommandLine commandLine, boolean withMediator) throws ExecutionException {
    this(commandLine);
  }

  protected KillableColoredProcessHandler(@NotNull Process process, @NotNull GeneralCommandLine commandLine) {
    super(process, commandLine);
    setShouldKillProcessSoftly(true);
  }

  /**
   * {@code commandLine} must not be empty (for correct thread attribution in the stacktrace)
   */
  public KillableColoredProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine) {
    super(process, commandLine);
    setShouldKillProcessSoftly(true);
  }

  /**
   * {@code commandLine} must not be empty (for correct thread attribution in the stacktrace)
   */
  public KillableColoredProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine, @NotNull Charset charset) {
    this(process, commandLine, charset, null);
  }

  /**
   * {@code commandLine} must not be empty (for correct thread attribution in the stacktrace)
   */
  public KillableColoredProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine, @NotNull Charset charset, @Nullable Set<File> filesToDelete) {
    super(process, commandLine, charset, filesToDelete);
    setShouldKillProcessSoftly(true);
  }

  public static class Silent extends KillableColoredProcessHandler {
    public Silent(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
      super(commandLine);
    }

    public Silent(@NotNull Process process, String commandLine, @NotNull Charset charset, @Nullable Set<File> filesToDelete) {
      super(process, commandLine, charset, filesToDelete);
    }

    @Override
    protected @NotNull BaseOutputReader.Options readerOptions() {
      return BaseOutputReader.Options.forMostlySilentProcess();
    }
  }
}
