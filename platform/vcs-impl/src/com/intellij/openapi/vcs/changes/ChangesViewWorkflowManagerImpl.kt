// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitPresentation
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.commit.*
import com.intellij.vcs.commit.CommitModeManager.Companion.subscribeOnCommitModeChange
import kotlinx.coroutines.flow.MutableStateFlow

internal class ChangesViewWorkflowManagerImpl(
  private val project: Project,
) : ChangesViewWorkflowManager(), Disposable {
  override val allowExcludeFromCommit = MutableStateFlow(false)
  override val editedCommit = MutableStateFlow<EditedCommitPresentation?>(null)

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

        val workflow = ChangesViewCommitWorkflow(project)
        val changesView = ChangesViewManager.getInstanceEx(project).getOrCreateCommitChangesView()
        val commitPanel = ChangesViewCommitPanel(project, changesView)
        setCommitWorkflowHandler(ChangesViewCommitWorkflowHandler(workflow, commitPanel))

        activity.end()
      }
      else {
        currentHandler.resetActivation()
      }
    }
    else {
      if (currentHandler != null) {
        Disposer.dispose(currentHandler)
        setCommitWorkflowHandler(null)
      }
    }
  }

  private fun setCommitWorkflowHandler(handler: ChangesViewCommitWorkflowHandler?) {
    _commitWorkflowHandler = handler
    if (handler != null) {
      handler.addActivityListener(object : ChangesViewCommitWorkflowHandler.ActivityListener {
        override fun activityStateChanged() {
          allowExcludeFromCommit.value = handler.isActive
        }
      })
      allowExcludeFromCommit.value = handler.isActive
    }
    else {
      allowExcludeFromCommit.value = false
    }
    setEditedCommit(null)
    project.messageBus.syncPublisher(TOPIC).commitWorkflowChanged()
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

  override fun setEditedCommit(editedCommit: EditedCommitPresentation?) {
    this.editedCommit.value = editedCommit
  }
}