// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface ProcessService {
  @NotNull
  Process startPtyProcess(String @NotNull [] command,
                          @Nullable String directory,
                          @NotNull Map<String, String> env,
                          @NotNull LocalPtyOptions options,
                          @Nullable Application app,
                          boolean redirectErrorStream,
                          boolean windowsAnsiColorEnabled,
                          boolean unixOpenTtyToPreserveOutputAfterTermination);

  boolean sendWinProcessCtrlC(@NotNull Process process);

  /**
   * pid is not enough to emulate CTRL+C on Windows, {@link java.lang.Process#getOutputStream} is also needed
   *
   * @deprecated use {@link #sendWinProcessCtrlC(Process)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  boolean sendWinProcessCtrlC(int pid);

  void killWinProcessRecursively(@NotNull Process process);

  boolean isLocalPtyProcess(@NotNull Process process);

  @Nullable
  Integer winPtyChildProcessId(@NotNull Process process);

  static @NotNull ProcessService getInstance() {
    return ApplicationManager.getApplication().getService(ProcessService.class);
  }

  void killWinProcess(int pid);
}
