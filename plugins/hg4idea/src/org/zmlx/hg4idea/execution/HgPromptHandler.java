// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.execution;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * HgPromptHandler is used by {@link HgPromptCommandExecutor, when you want to change the behavior of
 * standard commands execution in the Mercurial.
 */
public interface HgPromptHandler {

  /**
   * Checks you need to change the default behavior.
   *
   * @param message standard output message from Mercurial
   */
  boolean shouldHandle(@Nullable @NonNls String message);

  /**
   * Change default behavior in commands execution. Execute only if shouldHandle method return true.
   *
   * @param message       standard output message from Mercurial
   * @param choices       possible choices
   */
  HgPromptChoice promptUser(@NotNull @NlsSafe String message,
                            final HgPromptChoice @NotNull [] choices,
                            final @NotNull HgPromptChoice defaultChoice);
}
