// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk.br
import com.intellij.openapi.util.text.HtmlChunk.text
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsImplUtil.getShortVcsRootName
import com.intellij.xml.util.XmlStringUtil.escapeString
import git4idea.GitNotificationIdsHolder.Companion.STAGE_COMMIT_ERROR
import git4idea.GitNotificationIdsHolder.Companion.STAGE_COMMIT_SUCCESS
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitRepository

internal class GitStageShowNotificationCommitResultHandler(private val committer: GitStageCommitter) : CommitResultHandler {
  private val project get() = committer.project
  private val notifier get() = VcsNotifier.getInstance(project)

  override fun onSuccess(commitMessage: String) = reportResult()
  override fun onCancel() = reportResult()
  override fun onFailure(errors: MutableList<VcsException>) = reportResult()

  private fun reportResult() {
    reportSuccess(committer.successfulRepositories, committer.commitMessage)
    reportFailure(committer.failedRoots)
  }

  private fun reportSuccess(repositories: Collection<GitRepository>, commitMessage: String) {
    if (repositories.isEmpty()) return

    val repositoriesText = repositories.joinToString { "'${getShortVcsRootName(project, it.root)}'" }
    notifier.notifySuccess(
      STAGE_COMMIT_SUCCESS,
      "",
      message("stage.commit.successful", repositoriesText, escapeString(commitMessage))
    )
  }

  private fun reportFailure(failures: Map<VirtualFile, VcsException>) {
    if (failures.isEmpty()) return

    notifier.notifyError(
      STAGE_COMMIT_ERROR,
      message("stage.commit.failed", failures.keys.joinToString { "'${getShortVcsRootName(project, it)}'" }),
      HtmlBuilder().appendWithSeparators(br(), failures.values.map { text(it.localizedMessage) }).toString()
    )
  }
}