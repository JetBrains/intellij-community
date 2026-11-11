// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Runs a {@link ModPlanChain} for a user, on the EDT: the chain up to the next questions in a background read action under a modal
 * progress that the user can cancel, and each question in a dialog of its own on the EDT. A form starts with the values that its storage
 * remembers, and the storage keeps the answers after OK.
 * <p>
 * If PSI changed while the questions were open, or a write action interrupted the read action of the chain, the chain runs again from
 * its start with the answers so far, and the questions they answer are not asked again. In the unit-test mode nothing is asked.
 * <p>
 * If the EDT holds a read lock, the chain runs under the modal progress of the legacy flow, which shares the lock with its background
 * thread. Under a write action, the chain runs on the EDT, with no progress and no cancel. In both cases its questions are shown under
 * the lock.
 * <p>
 * Only for the EDT code of the interactive IDE flow; other callers should use {@link ModPlanCommandInterpreter}.
 *
 * @see ModPlanCommandInterpreter
 */
public interface ModPlanUiInterpreter {
  /**
   * @return an instance of this service
   */
  static @NotNull ModPlanUiInterpreter getInstance() {
    return ApplicationManager.getApplication().getService(ModPlanUiInterpreter.class);
  }

  /**
   * @param title     the title of the dialogs and of the progress windows
   * @param chain the call that gives the chain of {@code request}; called for every run
   * @return the result of the chain with the answers; null if the user cancelled a question or a progress
   */
  @RequiresEdt
  <T> @Nullable ModPlanChain.ChainResult.Finished<T> interpret(
    @NlsContexts.DialogTitle @NotNull String title,
    @NotNull ModPlanRequest request,
    @NotNull Function<ModPlanChainBuilder, ModPlanChain<T>> chain
  );
}
