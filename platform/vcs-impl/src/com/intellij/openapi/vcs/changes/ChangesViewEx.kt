// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler
import org.jetbrains.annotations.CalledInAny

interface ChangesViewEx : ChangesViewI {
  /**
   * Immediately reset changes view and request refresh when NON_MODAL modality allows (i.e. after a plugin was unloaded or a dialog closed)
   */
  @RequiresEdt
  fun resetViewImmediatelyAndRefreshLater()

  @CalledInAny
  fun scheduleRefresh(@RequiresBackgroundThread callback: Runnable)

  val isAllowExcludeFromCommit: Boolean

  @get:Deprecated("Use {@link ChangesViewWorkflowManager#getCommitWorkflowHandler}.")
  val commitWorkflowHandler: ChangesViewCommitWorkflowHandler?
}
