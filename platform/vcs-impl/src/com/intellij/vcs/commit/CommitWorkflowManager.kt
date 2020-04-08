// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.application.subscribe
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.ConfigImportHelper.isConfigImported
import com.intellij.openapi.application.ConfigImportHelper.isFirstSession
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
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vcs.impl.VcsInitObject
import com.intellij.util.messages.Topic
import com.intellij.vcs.commit.NonModalCommitUsagesCollector.logStateChanged
import java.util.*

private val isForceNonModalCommit get() = Registry.get("vcs.force.non.modal.commit")
private val appSettings get() = VcsApplicationSettings.getInstance()

internal class NonModalCommitCustomization : ApplicationInitializedListener {
  override fun componentsInitialized() {
    if (!isFirstSession() || isConfigImported()) return

    PropertiesComponent.getInstance().setValue(KEY, true)
    appSettings.COMMIT_FROM_LOCAL_CHANGES = true
    logStateChanged()
  }

  companion object {
    private const val KEY = "NonModalCommitCustomization.IsApplied"

    internal fun isNonModalCustomizationApplied(): Boolean = PropertiesComponent.getInstance().isTrueValue(KEY)
  }
}

class CommitWorkflowManager(private val project: Project) : ProjectComponent {

  override fun projectOpened() {
    ProjectLevelVcsManagerImpl.getInstanceImpl(project).addInitializationRequest(VcsInitObject.AFTER_COMMON) {
      runInEdt {
        subscribeToChanges()
        updateWorkflow()
      }
    }
  }

  private fun updateWorkflow() {
    if (project.isDisposed) return

    ChangesViewManager.getInstanceEx(project).updateCommitWorkflow()
    ChangesViewContentManager.getInstanceImpl(project)?.updateToolWindowMapping()
  }

  fun isNonModal(): Boolean {
    if (isForceNonModalCommit.asBoolean()) return true
    if (!appSettings.COMMIT_FROM_LOCAL_CHANGES) return false

    return canSetNonModal()
  }

  internal fun canSetNonModal(): Boolean {
    val activeVcses = ProjectLevelVcsManager.getInstance(project).allActiveVcss
    return activeVcses.isNotEmpty() && activeVcses.all { it.type == VcsType.distributed }
  }

  private fun subscribeToChanges() {
    if (project.isDisposed) return

    isForceNonModalCommit.addListener(object : RegistryValueListener {
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

    @JvmStatic
    fun setCommitFromLocalChanges(value: Boolean) {
      val oldValue = appSettings.COMMIT_FROM_LOCAL_CHANGES
      if (oldValue == value) return

      appSettings.COMMIT_FROM_LOCAL_CHANGES = value
      logStateChanged()
      getApplication().messageBus.syncPublisher(SETTINGS).settingsChanged()
    }

    internal fun isNonModalInSettings(): Boolean = isForceNonModalCommit.asBoolean() || appSettings.COMMIT_FROM_LOCAL_CHANGES
  }

  interface SettingsListener : EventListener {
    fun settingsChanged()
  }
}