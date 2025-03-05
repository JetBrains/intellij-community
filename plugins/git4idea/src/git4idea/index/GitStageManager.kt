// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.impl.LineStatusTrackerSettingListener
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.vcs.commit.CommitMode
import com.intellij.vcs.commit.CommitModeManager
import git4idea.GitVcs
import git4idea.commit.GitStagingAreaCommitMode
import git4idea.config.GitVcsApplicationSettings

internal class CommitModeListener(val project: Project) : CommitModeManager.CommitModeListener {
  override fun commitModeChanged() {
    ThreadingAssertions.assertEventDispatchThread()

    if (isStagingAreaAvailable(project)) {
      GitStageTracker.getInstance(project).updateTrackerState()
    }

    invokeLater {
      // Notify LSTM after CLM to let it save current partial changelists state
      ApplicationManager.getApplication().messageBus.syncPublisher(LineStatusTrackerSettingListener.TOPIC).settingsUpdated()
    }
  }
}

internal class GitStageStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (isStagingAreaAvailable(project)) {
      GitStageTracker.getInstance(project) // initialize tracker
    }
  }
}

internal fun stageLineStatusTrackerRegistryOption() = Registry.get("git.enable.stage.line.status.tracker")

fun enableStagingArea(enabled: Boolean) {
  val applicationSettings = GitVcsApplicationSettings.getInstance()
  if (enabled == applicationSettings.isStagingAreaEnabled) return

  applicationSettings.isStagingAreaEnabled = enabled
  ApplicationManager.getApplication().messageBus.syncPublisher(CommitModeManager.SETTINGS).settingsChanged()
}

internal fun isStagingAreaAvailable(project: Project): Boolean {
  val commitMode = CommitModeManager.getInstance(project).getCurrentCommitMode()
  return commitMode is GitStagingAreaCommitMode
}