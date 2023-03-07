// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.concurrency.isPending

internal class GitDelayedCommitTemplateMessageProvider : DelayedCommitMessageProvider {

  override fun init(project: Project, commitUi: CommitWorkflowUi) {
    val commitMessageUpdater = CommitMessageUpdater(project, commitUi)
    commitMessageUpdater.disableFieldUntilTemplateLoaded()

    project.messageBus.connect(commitUi).subscribe(GitCommitTemplateListener.TOPIC, commitMessageUpdater)
  }

  private inner class CommitMessageUpdater(private val project: Project,
                                           private val commitUi: CommitWorkflowUi) : GitCommitTemplateListener {
    private val templateTracker get() = project.service<GitCommitTemplateTracker>()
    private val vcsConfiguration get() = VcsConfiguration.getInstance(project)

    override fun notifyCommitTemplateChanged(repository: GitRepository) {
      runInEdt { updateCommitMessage() }
    }

    fun disableFieldUntilTemplateLoaded() {
      val initPromise = templateTracker.initPromise
      if (initPromise.isPending) {
        commitUi.commitMessageUi.startLoading()
        initPromise.onProcessed { _ ->
          // todo: ModalityState?
          runInEdt { commitUi.commitMessageUi.stopLoading() }
        }
      }
    }

    private fun updateCommitMessage() {
      if (vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) return

      val templateContent = GitCommitTemplateMessageProvider.getCommitMessage(project)
      if (templateContent != null) {
        vcsConfiguration.saveCommitMessage(commitUi.commitMessageUi.text)
        commitUi.commitMessageUi.text = templateContent
      }
    }
  }
}
