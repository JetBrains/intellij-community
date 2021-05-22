// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commit

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.vcs.commit.CommitWorkflowUi
import com.intellij.vcs.commit.DelayedCommitMessageProvider
import git4idea.repo.GitCommitTemplateListener
import git4idea.repo.GitCommitTemplateTracker
import git4idea.repo.GitRepository

internal class GitDelayedCommitTemplateMessageProvider : DelayedCommitMessageProvider {

  override fun init(project: Project, commitUi: CommitWorkflowUi, initialCommitMessagePolicy: () -> String?) {
    val initialCommitMessage = initialCommitMessagePolicy()
    val commitMessageUpdater = CommitMessageUpdater(project, commitUi, initialCommitMessage)

    project.messageBus.connect(commitUi).subscribe(GitCommitTemplateListener.TOPIC, commitMessageUpdater)
  }

  private inner class CommitMessageUpdater(private val project: Project,
                                           private val commitUi: CommitWorkflowUi,
                                           private val initialCommitMessage: String?) : GitCommitTemplateListener {
    init {
      startLoadingIfNeeded()
    }

    private val templateTracker get() = project.service<GitCommitTemplateTracker>()
    private val vcsConfiguration get() = VcsConfiguration.getInstance(project)

    override fun loadingFinished() {
      runInEdt { commitUi.commitMessageUi.stopLoading() }
    }

    override fun notifyCommitTemplateChanged(repository: GitRepository) {
      runInEdt { update(repository) }
    }

    private fun startLoadingIfNeeded() {
      val commitTemplateTrackingStarted = templateTracker.isStarted()
      if (!commitTemplateTrackingStarted && initialCommitMessage == null) {
        commitUi.commitMessageUi.startLoading()
      }
    }

    private fun update(repository: GitRepository? = null) {
      val templateContent = templateTracker.getTemplateContent(repository)

      if (templateContent != null && initialCommitMessage == null && !vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) {
        commitUi.commitMessageUi.text = templateContent
      }
    }
  }
}
