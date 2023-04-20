// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.ui.playback;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PlaybackCommandReporter {
  /**
   * Called by {@code PlaybackRunner} before the script command
   * @param fullCommandLine the name of the command with arguments, as written in the script
   */
  default void startOfCommand(@NotNull String fullCommandLine) { }

  /**
   * Called by {@code PlaybackRunner} after the script command has finished
   * @param errDescriptionOrNull the error description or {@code null}
   */
  default void endOfCommand(@Nullable String errDescriptionOrNull) { }

  /**
   * Called by {@code PlaybackRunner} before running the script
   * @param project the current project
   */
  default void startOfScript(@Nullable Project project) { }

  /**
   * Called by {@code PlaybackRunner} if the script is cancelled
   */
  default void scriptCanceled() { }

  /**
   * Called by {@code PlaybackRunner} after the script has finished executing
   * @param project the current project (may differ from what was at the start in {@code startOfScript})
   */
  default void endOfScript(@Nullable Project project) { }

  PlaybackCommandReporter EMPTY_PLAYBACK_COMMAND_REPORTER = new PlaybackCommandReporter() {
  };
}
