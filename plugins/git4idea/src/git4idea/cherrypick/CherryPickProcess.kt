// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.cherrypick

import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.GitActivity
import git4idea.GitApplyChangesProcess
import git4idea.actions.GitAbortOperationAction
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandlerListener
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.isCommitPublished
import git4idea.repo.GitRepository
import org.jetbrains.annotations.NonNls

internal class GitCherryPickProcess(
  project: Project,
  commits: List<VcsCommitMetadata>,
  private val indicator: ProgressIndicator?,
): GitApplyChangesProcess(
  project = project,
  commits = commits,
  forceAutoCommit = true,
  operationName = GitBundle.message("cherry.pick.name"),
  appliedWord = GitBundle.message("cherry.pick.applied"),
  abortCommand = GitAbortOperationAction.CherryPick(),
  preserveCommitMetadata = true,
  activityName = GitBundle.message("activity.name.cherry.pick"),
  activityId = GitActivity.CherryPick
) {
  private var successfullyCherryPickedCount = 0

  private val totalCommitsToCherryPick = commits.size

  private var currentCommitCounter = 0

  fun isSuccess() = successfullyCherryPickedCount == totalCommitsToCherryPick

  override fun isEmptyCommit(result: GitCommandResult): Boolean {
    val stdout = result.outputAsJoinedString
    val stderr = result.errorOutputAsJoinedString
    return stdout.contains("nothing to commit") ||
           stdout.contains("nothing added to commit but untracked files present") ||
           stderr.contains("previous cherry-pick is now empty")
  }

  /**
   * We control the cherry-pick workflow ourselves + we want to use partial commits ('git commit --only'), which is prohibited during
   * cherry-pick, i.e. until the CHERRY_PICK_HEAD exists.
   */
  override fun cleanupBeforeCommit(repository: GitRepository) {
    if (autoCommit) { // `git cherry-pick -n` doesn't create the CHERRY_PICK_HEAD
      removeCherryPickHead(repository)
    }
  }

  private fun removeCherryPickHead(repository: GitRepository) {
    val cherryPickHeadFile = repository.getRepositoryFiles().cherryPickHead
    if (cherryPickHeadFile.exists()) {
      val deleted = FileUtil.delete(cherryPickHeadFile)
      if (!deleted) {
        LOG.warn("Couldn't delete $cherryPickHeadFile")
      }
    }
    else {
      LOG.info("Cancel cherry-pick in " + repository.getPresentableUrl() + ": no CHERRY_PICK_HEAD found")
    }
  }

  override fun generateDefaultMessage(repository: GitRepository, commit: VcsCommitMetadata): @NonNls String {
    var message = commit.getFullMessage()
    if (shouldAddSuffix(repository, commit.getId())) {
      message += String.format("\n\n(cherry picked from commit %s)", commit.getId().asString()) //NON-NLS Do not i18n commit template
    }
    return message
  }

  override fun applyChanges(repository: GitRepository, commit: VcsCommitMetadata, listeners: List<GitLineHandlerListener>): GitCommandResult =
    cherryPickSingleCommit(repository, commit, listeners)

  override fun executeForCommit(repository: GitRepository, commit: VcsCommitMetadata, successfulCommits: MutableList<VcsCommitMetadata>, alreadyPicked: MutableList<VcsCommitMetadata>): Boolean {
    currentCommitCounter++
    val result = super.executeForCommit(repository, commit, successfulCommits, alreadyPicked)
    if (result) {
      successfullyCherryPickedCount++
    }
    return result
  }

  private fun cherryPickSingleCommit(
    repository: GitRepository,
    commit: VcsCommitMetadata,
    listeners: List<GitLineHandlerListener>,
  ): GitCommandResult {
    indicator?.let {
      updateCherryPickIndicatorText(it, commit)
    }
    val result = Git.getInstance().cherryPick(
      repository, commit.id.asString(), autoCommit, shouldAddSuffix(repository, commit.id),
      *listeners.toTypedArray()
    )
    indicator?.fraction = currentCommitCounter.toDouble() / totalCommitsToCherryPick
    return result
  }

  private fun updateCherryPickIndicatorText(indicator: ProgressIndicator, commit: VcsCommitMetadata) {
    indicator.text = if (totalCommitsToCherryPick > 1) {
      DvcsBundle.message(
        "cherry.picking.process.commit",
        StringUtil.trimMiddle(commit.subject, 30),
        currentCommitCounter,
        totalCommitsToCherryPick
      )
    } else {
      DvcsBundle.message(
        "cherry.picking.process.commit.single",
        StringUtil.trimMiddle(commit.subject, 30)
      )
    }
  }

  private fun shouldAddSuffix(repository: GitRepository, commit: Hash): Boolean =
    GitVcsSettings.getInstance(project).shouldAddSuffixToCherryPicksOfPublishedCommits() && isCommitPublished(repository, commit)

  companion object {
    private val LOG = thisLogger()
  }
}