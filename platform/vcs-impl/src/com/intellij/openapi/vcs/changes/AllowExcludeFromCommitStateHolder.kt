// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Service(Service.Level.PROJECT)
internal class AllowExcludeFromCommitStateHolder(project: Project) {
  private val _allowExcludeFromCommit = MutableStateFlow(false)
  val allowExcludeFromCommit: StateFlow<Boolean> = _allowExcludeFromCommit.asStateFlow()

  init {
    val workflowManager = ChangesViewWorkflowManager.getInstance(project)
    project.messageBus.connect().subscribe(ChangesViewWorkflowManager.TOPIC, ChangesViewWorkflowManager.ChangesViewWorkflowListener {
      registerListener(workflowManager.commitWorkflowHandler)
    })

    val currentWorkflowHandler = workflowManager.commitWorkflowHandler
    registerListener(currentWorkflowHandler)
    _allowExcludeFromCommit.value = currentWorkflowHandler?.isActive == true
  }

  private fun registerListener(workflowHandler: ChangesViewCommitWorkflowHandler?) {
    workflowHandler?.addActivityListener(object : ChangesViewCommitWorkflowHandler.ActivityListener {
      override fun activityStateChanged() {
        _allowExcludeFromCommit.value = workflowHandler.isActive == true
      }
    })
  }
}