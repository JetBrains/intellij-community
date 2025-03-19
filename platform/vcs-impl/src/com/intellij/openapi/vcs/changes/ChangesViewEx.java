// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ModalityState;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

public interface ChangesViewEx extends ChangesViewI {
  /**
   * @deprecated Changes no longer could be refreshed immediately, use {@link #promiseRefresh()} or {@link #scheduleRefresh()}
   */
  @Deprecated(forRemoval = true)
  void refreshImmediately();

  /**
   * Immediately reset changes view and request refresh when NON_MODAL modality allows (i.e. after a plugin was unloaded or a dialog closed)
   */
  @RequiresEdt
  void resetViewImmediatelyAndRefreshLater();

  default @NotNull Promise<?> promiseRefresh() {
    return promiseRefresh(ModalityState.nonModal());
  }

  /**
   * Promise is fulfilled on EDT under given modality state.
   */
  @NotNull Promise<?> promiseRefresh(@NotNull ModalityState modalityState);

  boolean isAllowExcludeFromCommit();

  /**
   * @deprecated Use {@link ChangesViewWorkflowManager#getCommitWorkflowHandler}.
   */
  @Deprecated(forRemoval = true)
  @Nullable
  ChangesViewCommitWorkflowHandler getCommitWorkflowHandler();
}
