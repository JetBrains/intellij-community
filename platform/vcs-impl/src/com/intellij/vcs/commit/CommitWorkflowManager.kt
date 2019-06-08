// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.application.subscribe
import com.intellij.openapi.application.runInEdt
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

private val isNonModalCommit = Registry.get("vcs.non.modal.commit")
private val appSettings = VcsApplicationSettings.getInstance()

internal class CommitWorkflowManager(private val project: Project) {
  private val changesViewManager = ChangesViewManager.getInstance(project) as ChangesViewManager
  private val vcsManager = ProjectLevelVcsManager.getInstance(project) as ProjectLevelVcsManagerImpl

  init {
    vcsManager.addInitializationRequest(VcsInitObject.AFTER_COMMON) {
      runInEdt {
        subscribeToChanges()
        updateWorkflow()
      }
    }
  }

  private fun updateWorkflow() = changesViewManager.updateCommitWorkflow(isNonModal())

  private fun isNonModal(): Boolean {
    if (isNonModalCommit.asBoolean()) return true
    if (!appSettings.COMMIT_FROM_LOCAL_CHANGES) return false

    return vcsManager.allActiveVcss.all { it.type == VcsType.distributed }
  }

  private fun subscribeToChanges() {
    isNonModalCommit.addListener(object : RegistryValueListener.Adapter() {
      override fun afterValueChanged(value: RegistryValue) = updateWorkflow()
    }, project)

    SETTINGS.subscribe(project, object : SettingsListener {
      override fun settingsChanged() = updateWorkflow()
    })

    VCS_CONFIGURATION_CHANGED.subscribe(project, VcsListener { runInEdt { updateWorkflow() } })
  }

  companion object {
    @JvmField
    val SETTINGS: Topic<SettingsListener> = Topic.create("Commit Workflow Settings", SettingsListener::class.java)

    @JvmStatic
    fun install(project: Project) = CommitWorkflowManager(project)
  }

  interface SettingsListener : EventListener {
    fun settingsChanged()
  }
}