// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.GitUtil
import git4idea.changes.GitChangeUtils
import git4idea.changes.GitChangeUtils.GitDiffChange
import git4idea.checkin.GitCheckinEnvironment.ChangedPath
import git4idea.checkin.GitCheckinEnvironment.Companion.getLogString
import git4idea.checkin.GitCheckinEnvironment.Companion.isCaseOnlyRename
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.util.GitFileUtils
import kotlin.collections.contains

internal class GitResetAddStagedChangesSaver(val repository: GitRepository) : GitStagedChangesSaver {
  private val excludedStagedChanges = mutableListOf<ChangedPath>()
  private val excludedUnstagedDeletions = mutableSetOf<FilePath>()

  override fun save(toCommitAdded: Set<FilePath>, toCommitRemoved: Set<FilePath>) {
    val project = repository.project
    val root = repository.root
    val rootPath = root.path

    validateNoUnmerged()

    val stagedChanges = GitChangeUtils.getStagedChanges(project, root)
    LOG.debug("Found staged changes: " + GitUtil.getLogStringGitDiffChanges(rootPath, stagedChanges))

    excludedStagedChanges.clear()
    val excludedStagedAdditions = mutableListOf<FilePath>()
    processExcludedPaths(stagedChanges, toCommitAdded, toCommitRemoved) { before, after ->
      if (before != null || after != null) excludedStagedChanges.add(ChangedPath(before, after))
      if (before == null && after != null) excludedStagedAdditions.add(after)
    }

    // Find files with 'AD' status, we will not be able to restore them after using 'git add' command,
    // getting "pathspec 'file.txt' did not match any files" error (and preventing us from adding other files).
    val unstagedChanges = GitChangeUtils.getUnstagedChanges(project, root, excludedStagedAdditions, false)
    LOG.debug("Found unstaged changes: " + GitUtil.getLogStringGitDiffChanges(rootPath, unstagedChanges))

    excludedUnstagedDeletions.clear()
    processExcludedPaths(unstagedChanges, toCommitAdded, toCommitRemoved) { before, after ->
      if (before != null && after == null) excludedUnstagedDeletions.add(before)
    }

    if (!excludedStagedChanges.isEmpty()) {
      // Reset staged changes which are not selected for commit
      LOG.info("Staged changes excluded for commit: " + getLogString(rootPath, excludedStagedChanges))
      resetExcluded(project, root, excludedStagedChanges)
    }
  }

  private fun validateNoUnmerged() {
    val unmergedFiles = GitChangeUtils.getUnmergedFiles(repository)
    if (unmergedFiles.isNotEmpty()) {
      throw VcsException(GitBundle.message("error.commit.cant.commit.with.unmerged.paths"))
    }
  }

  override fun load() {
    if (excludedStagedChanges.isNotEmpty()) {
      restoreExcluded()
    }
  }

  private fun processExcludedPaths(
    changes: Collection<GitDiffChange>,
    toCommitAdded: Set<FilePath>,
    toCommitRemoved: Set<FilePath>,
    function: (before: FilePath?, after: FilePath?) -> Unit,
  ) {
    for (change in changes) {
      var before = change.beforePath
      var after = change.afterPath
      if (toCommitRemoved.contains(before)) before = null
      if (toCommitAdded.contains(after)) after = null
      function(before, after)
    }
  }

  @Throws(VcsException::class)
  private fun resetExcluded(
    project: Project,
    root: VirtualFile,
    changes: Collection<ChangedPath>,
  ) {
    val allPaths: MutableSet<FilePath> = CollectionFactory.createCustomHashingStrategySet(ChangesUtil.CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY)
    for (change in changes) {
      ContainerUtil.addIfNotNull(allPaths, change.afterPath)
      ContainerUtil.addIfNotNull(allPaths, change.beforePath)
    }

    for (paths in VcsFileUtil.chunkPaths(root, allPaths)) {
      val handler = GitLineHandler(project, root, GitCommand.RESET).apply {
        endOptions()
        addParameters(paths)
      }
      Git.getInstance().runCommand(handler).throwOnError()
    }
  }

  private fun restoreExcluded() {
    val project = repository.project
    val root = repository.root
    val restoreExceptions = mutableListOf<VcsException>()

    val toAdd = mutableSetOf<FilePath>()
    val toRemove = mutableSetOf<FilePath>()

    for (change in excludedStagedChanges) {
      if (addAsCaseOnlyRename(project, root, change, restoreExceptions)) continue

      if (change.beforePath == null && excludedUnstagedDeletions.contains(change.afterPath)) {
        // we can't restore ADDED-DELETED files
        LOG.info("Ignored added-deleted staged change in " + change.afterPath)
        continue
      }

      ContainerUtil.addIfNotNull(toAdd, change.afterPath)
      ContainerUtil.addIfNotNull(toRemove, change.beforePath)
    }
    toRemove.removeAll(toAdd)

    LOG.debug(String.format("Restoring staged changes after commit: added: %s, removed: %s", toAdd, toRemove))
    GitFileUtils.stageForCommit(project, root, toAdd, toRemove, restoreExceptions)

    for (e in restoreExceptions) {
      LOG.warn(e)
    }
  }

  private fun addAsCaseOnlyRename(
    project: Project, root: VirtualFile, change: ChangedPath,
    exceptions: MutableList<in VcsException>,
  ): Boolean {
    try {
      if (!isCaseOnlyRename(change)) return false

      val beforePath = change.beforePath!!
      val afterPath = change.afterPath!!

      LOG.debug(String.format("Restoring staged case-only rename after commit: %s", change))
      val h = GitLineHandler(project, root, GitCommand.MV).apply {
        addParameters("-f", beforePath.path, afterPath.path)
      }
      Git.getInstance().runCommandWithoutCollectingOutput(h).throwOnError()
      return true
    }
    catch (e: VcsException) {
      exceptions.add(e)
      return false
    }
  }

  companion object {
    private val LOG = logger<GitResetAddStagedChangesSaver>()
  }
}
