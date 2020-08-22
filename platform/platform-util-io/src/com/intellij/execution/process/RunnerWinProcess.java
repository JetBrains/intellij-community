// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** @deprecated use {@link KillableColoredProcessHandler#KillableColoredProcessHandler(GeneralCommandLine, boolean)} */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
@SuppressWarnings({"unused", "DeprecatedIsStillUsed"})
public final class RunnerWinProcess extends ProcessWrapper {
  private RunnerWinProcess(@NotNull Process originalProcess) {
    super(originalProcess);
  }
}