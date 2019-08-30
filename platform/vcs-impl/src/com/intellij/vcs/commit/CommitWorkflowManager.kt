// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.application.subscribe
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vcs.VcsType
import com.intellij.openapi.vcs.changes.ChangesViewManager
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vcs.impl.VcsInitObject
import com.intellij.util.messages.Topic
import java.util.*

private val isForceNonModalCommit = Registry.get("vcs.force.non.modal.commit")
private val appSettings = VcsApplicationSettings.getInstance()

class CommitWorkflowManager(private val project: Project) : ProjectComponent {

  override fun projectOpened() {
    ProjectLevelVcsManagerImpl.getInstanceImpl(project).addInitializationRequest(VcsInitObject.AFTER_COMMON) {
      runInEdt {
        subscribeToChanges()
        updateWorkflow()
      }
    }
  }

  private fun updateWorkflow() = ChangesViewManager.getInstanceEx(project).updateCommitWorkflow()

  fun isNonModal(): Boolean {
    if (isForceNonModalCommit.asBoolean()) return true
    if (!appSettings.COMMIT_FROM_LOCAL_CHANGES) return false

    val activeVcses = ProjectLevelVcsManager.getInstance(project).allActiveVcss
    return activeVcses.isNotEmpty() && activeVcses.all { it.type == VcsType.distributed }
  }

  private fun subscribeToChanges() {
    isForceNonModalCommit.addListener(object : RegistryValueListener.Adapter() {
      override fun afterValueChanged(value: RegistryValue) = updateWorkflow()
    }, project)

    SETTINGS.subscribe(project, object : SettingsListener {
      override fun settingsChanged() = updateWorkflow()
    })

    project.messageBus.connect().subscribe(VCS_CONFIGURATION_CHANGED, VcsListener { runInEdt { updateWorkflow() } })
  }

  companion object {
    @JvmField
    val SETTINGS: Topic<SettingsListener> = Topic.create("Commit Workflow Settings", SettingsListener::class.java)

    @JvmStatic
    fun getInstance(project: Project): CommitWorkflowManager = project.getComponent(CommitWorkflowManager::class.java)
  }

  interface SettingsListener : EventListener {
    fun settingsChanged()
  }
}