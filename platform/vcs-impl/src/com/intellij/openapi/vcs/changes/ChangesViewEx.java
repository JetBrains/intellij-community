// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

public interface ChangesViewEx extends ChangesViewI {
  /**
   * @deprecated Changes no longer could be refreshed immediately, use {@link #promiseRefresh()} or {@link #scheduleRefresh()}
   */
  @Deprecated
  void refreshImmediately();

  /**
   * Immediately reset changes view and request refresh when NON_MODAL modality allows (i.e. after a plugin was unloaded or a dialog closed)
   */
  @RequiresEdt
  void resetViewImmediatelyAndRefreshLater();

  Promise<?> promiseRefresh();

  boolean isAllowExcludeFromCommit();

  /**
   * @deprecated Use {@link ChangesViewWorkflowManager#getCommitWorkflowHandler}.
   */
  @Deprecated
  @Nullable
  ChangesViewCommitWorkflowHandler getCommitWorkflowHandler();
}
