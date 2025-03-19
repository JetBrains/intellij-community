// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.coverage

import com.intellij.coverage.filters.ModifiedFilesFilter
import com.intellij.coverage.filters.ModifiedFilesFilterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.Hash
import git4idea.GitRemoteBranch
import git4idea.GitUtil
import git4idea.changes.GitChangeUtils
import git4idea.repo.GitRepository

internal class GitModifiedFilesFilterFactory : ModifiedFilesFilterFactory {
  override fun createFilter(project: Project): ModifiedFilesFilter? {
    if (!Registry.`is`("coverage.filter.based.on.feature.branch")) return null
    return createGitFilter(project)
  }
}

@RequiresBackgroundThread
private fun createGitFilter(project: Project): ModifiedFilesFilter? {
  val repositories = GitUtil.getRepositories(project)
  val filters = repositories.mapNotNull { createFilterForRepository(it) }
  if (filters.isEmpty()) return null
  return filters.singleOrNull() ?: MultiRepoGitModifiedFilesFilter(project, filters)
}

private fun createFilterForRepository(repository: GitRepository): GitModifiedFilesFilter? {
  val candidates = findBaseCommitCandidates(repository) ?: return null
  return candidates.mapNotNull { (hash, branch) ->
    val scope = createModifiedScope(repository, hash) ?: return@mapNotNull null
    GitModifiedFilesFilter(repository.project, scope, branch)
  }.minByOrNull { it.modifiedScope.size }
}

private fun findBaseCommitCandidates(repository: GitRepository): List<CurrentFeatureBranchBaseDetector.BaseCommitAndBranch>? {
  return when (val baseCommit = CurrentFeatureBranchBaseDetector(repository).findBaseCommit()) {
    is CurrentFeatureBranchBaseDetector.Status.Success -> baseCommit.commits
    else -> null
  }
}

private fun createModifiedScope(repository: GitRepository, baseRevision: Hash): Set<VirtualFile>? {
  val currentRevision = repository.currentRevision ?: return null
  val diff = GitChangeUtils.getDiff(repository, baseRevision.asString(), currentRevision, false) ?: return null
  val scope = hashSetOf<VirtualFile>()
  for (change in diff) {
    val virtualFile = change.afterRevision?.file?.virtualFile ?: continue
    scope.add(virtualFile)
  }
  return scope
}

private class GitModifiedFilesFilter(
  project: Project,
  val modifiedScope: Set<VirtualFile>,
  private val branch: GitRemoteBranch,
) : ModifiedFilesFilter(project) {
  override fun isInModifiedScope(file: VirtualFile) = file in modifiedScope
  override fun getBranchName(): String = branch.nameForLocalOperations
}

private class MultiRepoGitModifiedFilesFilter(
  project: Project,
  private val filters: List<GitModifiedFilesFilter>,
) : ModifiedFilesFilter(project) {
  override fun isInModifiedScope(file: VirtualFile) = filters.any { it.isFileModified(file) }
  override fun getBranchName() = "Protected"
}
