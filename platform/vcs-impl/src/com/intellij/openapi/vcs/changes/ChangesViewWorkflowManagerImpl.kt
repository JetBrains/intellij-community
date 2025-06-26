// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.commit.*
import com.intellij.vcs.commit.CommitModeManager.Companion.subscribeOnCommitModeChange

internal class ChangesViewWorkflowManagerImpl(
  private val project: Project,
) : ChangesViewWorkflowManager(), Disposable {

  private var _commitWorkflowHandler: ChangesViewCommitWorkflowHandler? = null

  private var isInitialized = false

  init {
    val busConnection = project.messageBus.connect(this)
    subscribeOnCommitModeChange(busConnection, object : CommitModeManager.CommitModeListener {
      override fun commitModeChanged() {
        updateCommitWorkflowHandler()
      }
    })
    ApplicationManager.getApplication().invokeLater({ updateCommitWorkflowHandler() }, ModalityState.nonModal(), project.disposed)
  }

  @RequiresEdt
  private fun updateCommitWorkflowHandler() {
    isInitialized = true

    val isNonModal = CommitModeManager.getInstance(project).getCurrentCommitMode() is CommitMode.NonModalCommitMode
    val currentHandler = _commitWorkflowHandler
    if (isNonModal) {
      if (currentHandler == null) {
        val activity = StartUpMeasurer.startActivity("ChangesViewWorkflowManager initialization")

        // ChangesViewPanel can be reused between workflow instances -> should clean up after ourselves
        val changesPanel = (ChangesViewManager.getInstance(project) as ChangesViewManager).initChangesPanel()
        val workflow = ChangesViewCommitWorkflow(project)
        val commitPanel = ChangesViewCommitPanel(project, changesPanel.changesView)
        _commitWorkflowHandler = ChangesViewCommitWorkflowHandler(workflow, commitPanel)

        project.messageBus.syncPublisher(TOPIC).commitWorkflowChanged()

        activity.end()
      }
      else {
        currentHandler.resetActivation()
      }
    }
    else {
      if (currentHandler != null) {
        Disposer.dispose(currentHandler)
        _commitWorkflowHandler = null

        project.messageBus.syncPublisher(TOPIC).commitWorkflowChanged()
      }
    }
  }

  override fun dispose() {
    val handler = _commitWorkflowHandler
    if (handler != null) {
      Disposer.dispose(handler)
      _commitWorkflowHandler = null
    }
  }

  override fun doGetCommitWorkflowHandler(): ChangesViewCommitWorkflowHandler? {
    if (ApplicationManager.getApplication().isDispatchThread && !isInitialized) {
      updateCommitWorkflowHandler()
    }
    return _commitWorkflowHandler
  }
}