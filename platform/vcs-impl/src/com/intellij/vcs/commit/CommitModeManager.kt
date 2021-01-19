// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.application.subscribe
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.ConfigImportHelper.isNewUser
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener
import com.intellij.openapi.vcs.impl.VcsEP
import com.intellij.openapi.vcs.impl.VcsInitObject
import com.intellij.openapi.vcs.impl.VcsStartupActivity
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.intellij.vcs.commit.NonModalCommitUsagesCollector.logStateChanged
import java.util.*

private val isToggleCommitUi get() = Registry.get("vcs.non.modal.commit.toggle.ui")
private val isForceNonModalCommit get() = Registry.get("vcs.force.non.modal.commit")
private val appSettings get() = VcsApplicationSettings.getInstance()

internal fun AnActionEvent.getProjectCommitMode(): CommitMode? =
  project?.let { CommitModeManager.getInstance(it).getCurrentCommitMode() }

internal class NonModalCommitCustomization : ApplicationInitializedListener {
  override fun componentsInitialized() {
    if (!isNewUser()) return

    PropertiesComponent.getInstance().setValue(KEY, true)
    appSettings.COMMIT_FROM_LOCAL_CHANGES = true
    logStateChanged(null)
  }

  companion object {
    private const val KEY = "NonModalCommitCustomization.IsApplied"

    internal fun isNonModalCustomizationApplied(): Boolean = PropertiesComponent.getInstance().isTrueValue(KEY)
  }
}

class CommitModeManager(private val project: Project) {
  class MyStartupActivity : VcsStartupActivity {
    override fun runActivity(project: Project) {
      runInEdt {
        if (project.isDisposed) return@runInEdt
        val commitModeManager = getInstance(project)
        commitModeManager.subscribeToChanges()
        commitModeManager.updateCommitMode()
      }
    }

    override fun getOrder(): Int = VcsInitObject.MAPPINGS.order + 50
  }

  private var commitMode: CommitMode = CommitMode.PendingCommitMode

  @RequiresEdt
  fun updateCommitMode() {
    if (project.isDisposed) return

    val newCommitMode = getNewCommitMode()
    if (commitMode == newCommitMode) return
    commitMode = newCommitMode

    project.messageBus.syncPublisher(COMMIT_MODE_TOPIC).commitModeChanged()
  }

  private fun getNewCommitMode(): CommitMode {
    val activeVcses = ProjectLevelVcsManager.getInstance(project).allActiveVcss
    val singleVcs = activeVcses.singleOrNull()

    if (activeVcses.isEmpty()) return CommitMode.PendingCommitMode

    if (singleVcs != null && singleVcs.isWithCustomLocalChanges) {
      return CommitMode.ExternalCommitMode(singleVcs)
    }

    if (isNonModalInSettings() && canSetNonModal()) {
      val isToggleMode = isToggleCommitUi.asBoolean()
      return CommitMode.NonModalCommitMode(isToggleMode)
    }

    return CommitMode.ModalCommitMode
  }

  fun getCurrentCommitMode(): CommitMode {
    return commitMode
  }

  internal fun canSetNonModal(): Boolean {
    if (isForceNonModalCommit.asBoolean()) return true
    val activeVcses = ProjectLevelVcsManager.getInstance(project).allActiveVcss
    return activeVcses.isNotEmpty() && activeVcses.all { it.type == VcsType.distributed }
  }

  private fun subscribeToChanges() {
    if (project.isDisposed) return

    isForceNonModalCommit.addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) = updateCommitMode()
    }, project)
    isToggleCommitUi.addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) = updateCommitMode()
    }, project)
    SETTINGS.subscribe(project, object : SettingsListener {
      override fun settingsChanged() = updateCommitMode()
    })

    VcsEP.EP_NAME.addChangeListener(Runnable { updateCommitMode() }, project)
    project.messageBus.connect().subscribe(VCS_CONFIGURATION_CHANGED, VcsListener { runInEdt { updateCommitMode() } })
  }

  companion object {
    @JvmField
    val SETTINGS: Topic<SettingsListener> = Topic(SettingsListener::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true)

    @JvmField
    val COMMIT_MODE_TOPIC: Topic<CommitModeListener> = Topic(CommitModeListener::class.java, Topic.BroadcastDirection.NONE, true)

    @JvmStatic
    fun getInstance(project: Project): CommitModeManager = project.service()

    @JvmStatic
    fun setCommitFromLocalChanges(project: Project?, value: Boolean) {
      val oldValue = appSettings.COMMIT_FROM_LOCAL_CHANGES
      if (oldValue == value) return

      appSettings.COMMIT_FROM_LOCAL_CHANGES = value
      logStateChanged(project)
      getApplication().messageBus.syncPublisher(SETTINGS).settingsChanged()
    }

    fun isNonModalInSettings(): Boolean = isForceNonModalCommit.asBoolean() || appSettings.COMMIT_FROM_LOCAL_CHANGES
  }

  interface SettingsListener : EventListener {
    fun settingsChanged()
  }

  interface CommitModeListener : EventListener {
    @RequiresEdt
    fun commitModeChanged()
  }
}

sealed class CommitMode {
  abstract fun useCommitToolWindow(): Boolean
  open fun hideLocalChangesTab(): Boolean = false

  object PendingCommitMode : CommitMode() {
    override fun useCommitToolWindow(): Boolean {
      // Enable 'Commit' toolwindow before vcses are activated
      return CommitModeManager.isNonModalInSettings()
    }
  }

  object ModalCommitMode : CommitMode() {
    override fun useCommitToolWindow(): Boolean = false
  }

  data class NonModalCommitMode(val isToggleMode: Boolean) : CommitMode() {
    override fun useCommitToolWindow(): Boolean = true
  }

  data class ExternalCommitMode(val vcs: AbstractVcs) : CommitMode() {
    override fun useCommitToolWindow(): Boolean = true
    override fun hideLocalChangesTab(): Boolean = true
  }
}