// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector
import com.intellij.openapi.vcs.impl.VcsEP
import com.intellij.openapi.vcs.impl.VcsInitObject
import com.intellij.openapi.vcs.impl.VcsStartupActivity
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.SimpleMessageBusConnection
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.CalledInAny
import java.util.*

private const val TOGGLE_COMMIT_UI = "vcs.non.modal.commit.toggle.ui"

private val isToggleCommitUi get() = AdvancedSettings.getBoolean(TOGGLE_COMMIT_UI)
private val isForceNonModalCommit get() = Registry.get("vcs.force.non.modal.commit")
private val appSettings get() = VcsApplicationSettings.getInstance()

internal fun AnActionEvent.getProjectCommitMode(): CommitMode? {
  return project?.let { CommitModeManager.getInstance(it).getCurrentCommitMode() }
}

@Service(Service.Level.PROJECT)
class CommitModeManager(private val project: Project, private val coroutineScope: CoroutineScope) : Disposable {
  internal class MyStartupActivity : VcsStartupActivity {
    override val order: Int
      get() = VcsInitObject.MAPPINGS.order + 50

    override suspend fun execute(project: Project) {
      @Suppress("TestOnlyProblems")
      if (project is ProjectEx && project.isLight) {
        return
      }

      val commitModeManager = project.serviceAsync<CommitModeManager>()
      commitModeManager.coroutineScope.launch(Dispatchers.EDT) {
        //maybe readaction
        writeIntentReadAction {
          commitModeManager.subscribeToChanges()
          commitModeManager.updateCommitMode()
        }
      }
    }
  }

  private var commitMode: CommitMode = CommitMode.PendingCommitMode

  private fun scheduleUpdateCommitMode() {
    getApplication().invokeLater(::updateCommitMode, ModalityState.nonModal(), project.disposed)
  }

  @RequiresEdt
  private fun updateCommitMode() {
    val newCommitMode = getNewCommitMode()
    if (commitMode == newCommitMode) {
      return
    }
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
      return CommitMode.NonModalCommitMode(isToggleCommitUi)
    }

    return CommitMode.ModalCommitMode
  }

  @CalledInAny
  fun getCurrentCommitMode() = commitMode

  private fun canSetNonModal(): Boolean {
    if (isForceNonModalCommit.asBoolean()) return true
    val activeVcses = ProjectLevelVcsManager.getInstance(project).allActiveVcss
    return activeVcses.isNotEmpty() && activeVcses.all { it.type == VcsType.distributed }
  }

  private fun subscribeToChanges() {
    isForceNonModalCommit.addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) = updateCommitMode()
    }, coroutineScope)

    val connection = getApplication().messageBus.connect(coroutineScope)
    connection.subscribe(AdvancedSettingsChangeListener.TOPIC, object : AdvancedSettingsChangeListener {
      override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
        if (id == TOGGLE_COMMIT_UI) {
          updateCommitMode()
        }
      }
    })
    connection.subscribe(SETTINGS, object : SettingsListener {
      override fun settingsChanged() = updateCommitMode()
    })

    VcsEP.EP_NAME.addChangeListener(::scheduleUpdateCommitMode, this)
    project.messageBus.connect(coroutineScope).subscribe(VCS_CONFIGURATION_CHANGED, VcsListener(::scheduleUpdateCommitMode))
  }

  companion object {
    @JvmField
    @Topic.AppLevel
    val SETTINGS: Topic<SettingsListener> = Topic(SettingsListener::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true)

    @Topic.ProjectLevel
    private val COMMIT_MODE_TOPIC: Topic<CommitModeListener> = Topic(CommitModeListener::class.java, Topic.BroadcastDirection.NONE, true)

    @JvmStatic
    fun subscribeOnCommitModeChange(connection: SimpleMessageBusConnection, listener: CommitModeListener) {
      connection.subscribe(COMMIT_MODE_TOPIC, listener)
    }

    @JvmStatic
    fun getInstance(project: Project): CommitModeManager = project.service()

    @JvmStatic
    fun setCommitFromLocalChanges(project: Project?, value: Boolean) {
      val oldValue = appSettings.COMMIT_FROM_LOCAL_CHANGES
      if (oldValue == value) return

      appSettings.COMMIT_FROM_LOCAL_CHANGES = value
      VcsStatisticsCollector.logNonModalCommitStateChanged(project)
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

  override fun dispose() {
  }
}

sealed class CommitMode {
  abstract fun useCommitToolWindow(): Boolean
  open fun hideLocalChangesTab(): Boolean = false
  open fun disableDefaultCommitAction(): Boolean = false

  object PendingCommitMode : CommitMode() {
    override fun useCommitToolWindow(): Boolean {
      // Enable 'Commit' toolwindow before vcses are activated
      return CommitModeManager.isNonModalInSettings()
    }

    override fun disableDefaultCommitAction(): Boolean {
      // Disable `Commit` action until vcses are activated
      return true
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
    override fun disableDefaultCommitAction(): Boolean = true
  }
}