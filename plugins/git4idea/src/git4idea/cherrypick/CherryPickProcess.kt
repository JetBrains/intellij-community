// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.cherrypick

import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil.delete
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsException
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.GitActivity
import git4idea.GitUtil
import git4idea.GitUtil.CHERRY_PICK_HEAD
import git4idea.actions.GitAbortOperationAction
import git4idea.applyChanges.GitApplyChangesProcess
import git4idea.cherrypick.GitCherryPickContinueProcess.isEmptyCommit
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandlerListener
import git4idea.config.GitVcsApplicationSettings
import git4idea.config.GitVcsSettings
import git4idea.history.GitHistoryUtils
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
  operationName = GitBundle.message("cherry.pick.name"),
  appliedWord = GitBundle.message("cherry.pick.applied"),
  abortCommand = GitAbortOperationAction.CherryPick(),
  preserveCommitMetadata = true,
  activityName = GitBundle.message("activity.name.cherry.pick"),
  activityId = GitActivity.CherryPick
) {
  private var successfullyCherryPickedCount = 0

  private val totalCommitsToCherryPick = commits.size

  private var currentCommitCounter = 1
  private lateinit var currentCommits: Collection<VcsCommitMetadata>

  fun isSuccess() = successfullyCherryPickedCount == totalCommitsToCherryPick

  override fun isEmptyCommit(result: GitCommandResult) = result.isEmptyCommit()

  override fun findStoppedCommitInSequence(repository: GitRepository, commits: List<VcsCommitMetadata>): VcsCommitMetadata {
    if (commits.size == 1) return commits.first()
    // Prefer CHERRY_PICK_HEAD if present, default to next after head otherwise
    return repository.getCherryPickHead() ?: run {
      LOG.warn("Failed to get CHERRY_PICK_HEAD")
      val head = GitUtil.getHead(repository)
      val nextIndexAfterHead = commits.indexOfLast { head == it.id } + 1
      commits[nextIndexAfterHead.coerceIn(0, commits.lastIndex)]
    }
  }

  /**
   * We control the cherry-pick workflow ourselves + we want to use partial commits ('git commit --only'), which is prohibited during
   * cherry-pick, i.e. until the CHERRY_PICK_HEAD exists.
   */
  override fun cleanupBeforeCommit(repository: GitRepository) {
    val cherryPickHeadFile = repository.getRepositoryFiles().cherryPickHead
    if (cherryPickHeadFile.exists()) {
      val deleted = delete(cherryPickHeadFile)
      if (!deleted) {
        LOG.warn("Couldn't delete $cherryPickHeadFile")
      }
    } else {
      LOG.info("Cancel cherry-pick in " + repository.getPresentableUrl() + ": no CHERRY_PICK_HEAD found")
    }
  }

  override fun generateDefaultMessage(repository: GitRepository, commit: VcsCommitMetadata): @NonNls String = buildString {
    append(commit.fullMessage)
    if (shouldAddSuffix(repository, commit.getId())) {
      append(String.format("\n\n(cherry picked from commit %s)", commit.getId().asString())) //NON-NLS Do not i18n commit template
    }
  }

  // Handle all the given commits in a single operation if registry is enabled
  override fun executeForRepository(repository: GitRepository, repoCommits: List<VcsCommitMetadata>, successfulCommits: MutableSet<VcsCommitMetadata>, alreadyPicked: MutableSet<VcsCommitMetadata>): Boolean {
    return if (Registry.`is`("git.cherry.pick.use.git.sequencer")) {
      executeForCommitChunk(repository, repoCommits, successfulCommits, alreadyPicked)
    }
    else {
      super.executeForRepository(repository, repoCommits, successfulCommits, alreadyPicked)
    }
  }

  override fun onSuccessfulCommitsAdded(commits: Collection<VcsCommitMetadata>) {
    currentCommitCounter += commits.size
    updateCherryPickIndicator()
  }

  override fun executeForCommitChunk(repository: GitRepository, commits: List<VcsCommitMetadata>, successfulCommits: MutableSet<VcsCommitMetadata>, alreadyPicked: MutableSet<VcsCommitMetadata>): Boolean {
    val result = super.executeForCommitChunk(repository, commits, successfulCommits, alreadyPicked)
    if (result) {
      successfullyCherryPickedCount += commits.size
      val lastCommit = commits.last()
      if (alreadyPicked.lastOrNull() == lastCommit) {
        LOG.info("Applying empty cherry-pick resolution strategy, as the last commit ${lastCommit.id} in the sequence is empty")
        GitVcsApplicationSettings.getInstance().emptyCherryPickResolutionStrategy.apply(repository)
      }
    }
    return result
  }

  override fun applyChanges(repository: GitRepository, commits: Collection<VcsCommitMetadata>, listeners: List<GitLineHandlerListener>): GitCommandResult {
    currentCommits = commits
    updateCherryPickIndicator()
    return Git.getInstance().cherryPick(
      repository,
      commits.map { it.id.asString() },
      AUTO_COMMIT,
      commits.all { commit -> shouldAddSuffix(repository, commit.id) },
      *listeners.toTypedArray()
    )
  }

  private fun updateCherryPickIndicator() {
    indicator?.fraction = currentCommitCounter.toDouble() / totalCommitsToCherryPick

    indicator?.text = if (totalCommitsToCherryPick > 1) {
      DvcsBundle.message(
        "cherry.picking.process.commit",
        StringUtil.trimMiddle(currentCommits.joinToString { it.subject }, 30),
        currentCommitCounter,
        totalCommitsToCherryPick
      )
    } else {
      DvcsBundle.message(
        "cherry.picking.process.commit.single",
        StringUtil.trimMiddle(currentCommits.joinToString { it.subject }, 30)
      )
    }
  }

  private fun shouldAddSuffix(repository: GitRepository, commit: Hash): Boolean =
    GitVcsSettings.getInstance(project).shouldAddSuffixToCherryPicksOfPublishedCommits() && isCommitPublished(repository, commit)

  companion object {
    private val LOG = thisLogger()
    private val AUTO_COMMIT = true

    internal fun GitRepository.getCherryPickHead(): VcsCommitMetadata? = try {
      GitHistoryUtils.collectCommitsMetadata(project, root, CHERRY_PICK_HEAD)?.firstOrNull()
    } catch (_ : VcsException) {
      null
    }
  }
}