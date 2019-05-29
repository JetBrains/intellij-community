// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher

class AmendCommitHandlerImpl(private val workflowHandler: AbstractCommitWorkflowHandler<*, *>) : AmendCommitHandler {
  private val amendCommitEventDispatcher = EventDispatcher.create(AmendCommitModeListener::class.java)

  private val workflow get() = workflowHandler.workflow
  private val commitContext get() = workflow.commitContext

  override var isAmendCommitMode: Boolean
    get() = commitContext.isAmendCommitMode
    set(value) {
      val oldValue = isAmendCommitMode
      commitContext.isAmendCommitMode = value

      if (oldValue != value) amendCommitEventDispatcher.multicaster.amendCommitModeToggled()
    }

  override var isAmendCommitModeTogglingEnabled: Boolean = true

  override fun isAmendCommitModeSupported(): Boolean =
    workflow.isDefaultCommitEnabled &&
    workflow.vcses.mapNotNull { it.checkinEnvironment }.filterIsInstance<AmendCommitAware>().any { it.isAmendCommitSupported() }

  override fun addAmendCommitModeListener(listener: AmendCommitModeListener, parent: Disposable) =
    amendCommitEventDispatcher.addListener(listener, parent)
}