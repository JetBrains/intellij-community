// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.vcs.commit.CommitMessageUi
import com.intellij.vcs.commit.DelayedCommitMessageProvider
import git4idea.repo.GitCommitTemplateListener
import git4idea.repo.GitCommitTemplateTracker
import git4idea.repo.GitRepository
import org.jetbrains.concurrency.isPending

internal class GitTemplateCommitMessageProvider : DelayedCommitMessageProvider {
  override fun getCommitMessage(forChangelist: LocalChangeList, project: Project): String? {
    return getTemplateCommitMessage(project)
  }

  override fun init(project: Project, commitUi: CommitMessageUi, disposable: Disposable) {
    val commitMessageUpdater = GitCommitTemplateMessageUpdater(project, commitUi)
    commitMessageUpdater.disableFieldUntilTemplateLoaded()

    project.messageBus.connect(disposable).subscribe(GitCommitTemplateListener.TOPIC, commitMessageUpdater)
  }
}

private class GitCommitTemplateMessageUpdater(private val project: Project,
                                              private val commitUi: CommitMessageUi) : GitCommitTemplateListener {
  private var previousTemplate: String? = null

  private val templateTracker get() = GitCommitTemplateTracker.getInstance(project)
  private val vcsConfiguration get() = VcsConfiguration.getInstance(project)

  init {
    previousTemplate = getTemplateCommitMessage(project)
  }

  override fun notifyCommitTemplateChanged(repository: GitRepository) {
    runInEdt { updateCommitMessage() }
  }

  fun disableFieldUntilTemplateLoaded() {
    if (templateTracker.initPromise.isPending) {
      commitUi.startLoading()
      templateTracker.initPromise.onProcessed { _ ->
        // todo: ModalityState?
        runInEdt { commitUi.stopLoading() }
      }
    }
  }

  private fun updateCommitMessage() {
    if (project.isDisposed) return
    if (vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) return

    val templateContent = getTemplateCommitMessage(project)
    if (templateContent != null && previousTemplate != templateContent) {
      vcsConfiguration.saveCommitMessage(commitUi.text)
      commitUi.text = templateContent
      previousTemplate = templateContent
    }
  }
}

internal fun getTemplateCommitMessage(project: Project): String? {
  return GitCommitTemplateTracker.getInstance(project).getTemplateContent()
}
