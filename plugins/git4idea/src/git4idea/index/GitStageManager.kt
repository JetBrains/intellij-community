// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangesViewManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener
import com.intellij.openapi.vcs.impl.LineStatusTrackerSettingListener
import com.intellij.util.messages.Topic
import com.intellij.vcs.commit.CommitWorkflowManager
import git4idea.GitVcs
import git4idea.config.GitVcsApplicationSettings

class GitStageManager(val project: Project) : Disposable {

  fun installListeners() {
    val connection = project.messageBus.connect(this)
    connection.subscribe(GitStagingAreaSettingsListener.TOPIC, object : GitStagingAreaSettingsListener {
      override fun settingsChanged() {
        onAvailabilityChanged()
        ChangesViewManager.getInstanceEx(project).updateCommitWorkflow()
        project.messageBus.syncPublisher(ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged()
      }
    })
    connection.subscribe(CommitWorkflowManager.SETTINGS, object : CommitWorkflowManager.SettingsListener {
      override fun settingsChanged() {
        onAvailabilityChanged()
      }
    })
    stageLocalChangesRegistryOption().addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        project.messageBus.syncPublisher(ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged()
        ApplicationManager.getApplication().messageBus.syncPublisher(LineStatusTrackerSettingListener.TOPIC).settingsUpdated()
      }
    }, this)
    stageLineStatusTrackerRegistryOption().addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        ApplicationManager.getApplication().messageBus.syncPublisher(LineStatusTrackerSettingListener.TOPIC).settingsUpdated()
      }
    }, this)
  }

  private fun onAvailabilityChanged() {
    if (isStageAvailable(project)) {
      GitStageTracker.getInstance(project).scheduleUpdateAll()
    }
    ApplicationManager.getApplication().messageBus.syncPublisher(LineStatusTrackerSettingListener.TOPIC).settingsUpdated()
  }

  override fun dispose() {
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.getService(GitStageManager::class.java)
  }
}

class GitStageStartupActivity : StartupActivity.Background {
  override fun runActivity(project: Project) {
    if (isStageAvailable(project)) {
      GitStageTracker.getInstance(project).scheduleUpdateAll()
    }
    GitStageManager.getInstance(project).installListeners()
  }
}

interface GitStagingAreaSettingsListener {
  fun settingsChanged()

  companion object {
    @JvmField
    val TOPIC: Topic<GitStagingAreaSettingsListener> = Topic.create("Git Staging Area Settings Changes",
                                                                    GitStagingAreaSettingsListener::class.java)
  }
}

fun stageLineStatusTrackerRegistryOption() = Registry.get("git.enable.stage.line.status.tracker")
fun stageLocalChangesRegistryOption() = Registry.get("git.enable.stage.disable.local.changes")

fun isStagingAreaEnabled() = GitVcsApplicationSettings.getInstance().isStagingAreaEnabled
fun enableStagingArea(enabled: Boolean) {
  val applicationSettings = GitVcsApplicationSettings.getInstance()
  if (enabled == applicationSettings.isStagingAreaEnabled) return

  applicationSettings.isStagingAreaEnabled = enabled
  ApplicationManager.getApplication().messageBus.syncPublisher(GitStagingAreaSettingsListener.TOPIC).settingsChanged()
}

fun canEnableStagingArea() = CommitWorkflowManager.isNonModalInSettings()

fun isStagingAreaAvailable() = isStagingAreaEnabled() && canEnableStagingArea()
fun isStageAvailable(project: Project): Boolean {
  return isStagingAreaAvailable() &&
         ProjectLevelVcsManager.getInstance(project).singleVCS?.keyInstanceMethod == GitVcs.getKey()
}