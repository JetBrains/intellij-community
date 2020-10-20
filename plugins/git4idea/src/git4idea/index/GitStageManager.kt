// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangesViewManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener
import com.intellij.openapi.vcs.impl.LineStatusTrackerSettingListener
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.intellij.vcs.commit.CommitWorkflowManager
import git4idea.GitVcs
import git4idea.config.GitVcsApplicationSettings

internal class GitStageManager {
  companion object {
    @RequiresEdt
    private fun onAvailabilityChanged(project: Project) {
      ApplicationManager.getApplication().assertIsDispatchThread()

      if (isStagingAreaAvailable(project)) {
        GitStageTracker.getInstance(project).scheduleUpdateAll()
      }
      project.messageBus.syncPublisher(ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged()
      ChangesViewManager.getInstanceEx(project).updateCommitWorkflow()
      // Notify LSTM after CLM to let it save current partial changelists state
      ApplicationManager.getApplication().messageBus.syncPublisher(LineStatusTrackerSettingListener.TOPIC).settingsUpdated()
    }
  }

  internal class GitStageStartupActivity : StartupActivity.Background {
    override fun runActivity(project: Project) {
      if (isStagingAreaAvailable(project)) {
        GitStageTracker.getInstance(project).scheduleUpdateAll()
      }
    }
  }

  internal class StagingSettingsListener(val project: Project) : GitStagingAreaSettingsListener {
    override fun settingsChanged() {
      onAvailabilityChanged(project)
    }
  }

  internal class CommitSettingsListener(val project: Project) : CommitWorkflowManager.SettingsListener {
    override fun settingsChanged() {
      onAvailabilityChanged(project)
    }
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
fun isStagingAreaAvailable(project: Project): Boolean {
  return isStagingAreaAvailable() &&
         ProjectLevelVcsManager.getInstance(project).singleVCS?.keyInstanceMethod == GitVcs.getKey()
}