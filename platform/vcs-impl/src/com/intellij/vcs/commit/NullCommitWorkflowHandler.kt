// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.changes.CommitExecutor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object NullCommitWorkflowHandler : CommitWorkflowHandler {
  override val amendCommitHandler: AmendCommitHandler = NullAmendCommitHandler

  override fun getExecutor(executorId: String): CommitExecutor? = null
  override fun isExecutorEnabled(executor: CommitExecutor): Boolean = false
  override fun execute(executor: CommitExecutor) = Unit
}

@ApiStatus.Internal
@Suppress("UNUSED_PARAMETER")
object NullAmendCommitHandler : AmendCommitHandler {
  override var isAmendCommitMode: Boolean
    get() = false
    set(value) = Unit

  override var isAmendCommitModeTogglingEnabled: Boolean
    get() = false
    set(value) = Unit

  override fun isAmendCommitModeSupported(): Boolean = false

  override fun addAmendCommitModeListener(listener: AmendCommitModeListener, parent: Disposable) = Unit
}