// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.changes.viewModel.ChangesViewProxy
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface ChangesViewEx : ChangesViewI {
  @RequiresEdt
  @ApiStatus.Internal
  fun getOrCreateCommitChangesView(): ChangesViewProxy

  @get:ApiStatus.ScheduledForRemoval
  @get:Deprecated("Use {@link ChangesViewWorkflowManager#getCommitWorkflowHandler}.")
  val commitWorkflowHandler: ChangesViewCommitWorkflowHandler?
}
