// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.CommitExecutor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class NullCommitWorkflowHandler(project: Project) : CommitWorkflowHandler {
  override val amendCommitHandler: AmendCommitHandler = NullAmendCommitHandler(project)

  override fun getExecutor(executorId: String): CommitExecutor? = null
  override fun isExecutorEnabled(executor: CommitExecutor): Boolean = false
  override fun execute(executor: CommitExecutor) = Unit
}

@ApiStatus.Internal
@Suppress("UNUSED_PARAMETER")
class NullAmendCommitHandler(override val project: Project) : AmendCommitHandler {
  override var commitToAmend: CommitToAmend
    get() = CommitToAmend.None
    set(value) = Unit

  override var isAmendCommitModeTogglingEnabled: Boolean
    get() = false
    set(value) = Unit

  override fun isAmendCommitModeSupported(): Boolean = false
  override fun isAmendSpecificCommitSupported(): Boolean = false

  override fun addAmendCommitModeListener(listener: AmendCommitModeListener, parent: Disposable) = Unit
}