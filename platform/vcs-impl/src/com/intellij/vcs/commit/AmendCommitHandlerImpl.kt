// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

class AmendCommitHandlerImpl(private val workflowHandler: AbstractCommitWorkflowHandler<*, *>) : AmendCommitHandler {
  private val workflow get() = workflowHandler.workflow
  private val commitContext get() = workflow.commitContext

  override var isAmendCommitMode: Boolean
    get() = commitContext.isAmendCommitMode
    set(value) {
      commitContext.isAmendCommitMode = value
    }

  override var isAmendCommitModeTogglingEnabled: Boolean = true

  override fun isAmendCommitModeSupported(): Boolean =
    workflow.isDefaultCommitEnabled &&
    workflow.vcses.mapNotNull { it.checkinEnvironment }.filterIsInstance<AmendCommitAware>().any { it.isAmendCommitSupported() }
}