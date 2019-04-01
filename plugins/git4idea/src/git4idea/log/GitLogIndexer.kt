// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.Consumer
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.impl.VcsLogIndexer
import git4idea.GitVcs
import git4idea.history.GitCommitRequirements
import git4idea.history.GitCommitRequirements.DiffInMergeCommits
import git4idea.history.GitCommitRequirements.DiffRenameLimit
import git4idea.history.GitDetailsCollector
import git4idea.history.GitLogUtil
import git4idea.log.GitLogProvider.isRepositoryReady
import git4idea.log.GitLogProvider.shouldIncludeRootChanges
import git4idea.repo.GitRepositoryManager

class GitLogIndexer(private val project: Project,
                    private val repositoryManager: GitRepositoryManager) : VcsLogIndexer {

  @Throws(VcsException::class)
  override fun readAllFullDetails(root: VirtualFile, commitConsumer: Consumer<in VcsFullCommitDetails>) {
    if (!isRepositoryReady(repositoryManager, root)) {
      return
    }

    val requirements = GitCommitRequirements(shouldIncludeRootChanges(repositoryManager, root), DiffRenameLimit.REGISTRY,
                                             DiffInMergeCommits.DIFF_TO_PARENTS, false)
    GitDetailsCollector(project, root).readFullDetails(commitConsumer, requirements, true,
                                                       *ArrayUtil.toStringArray(GitLogUtil.LOG_ALL))
  }

  @Throws(VcsException::class)
  override fun readFullDetails(root: VirtualFile,
                               hashes: List<String>,
                               commitConsumer: Consumer<in VcsFullCommitDetails>,
                               fast: Boolean) {
    if (!isRepositoryReady(repositoryManager, root)) {
      return
    }

    val renameLimit = if (fast) DiffRenameLimit.REGISTRY else DiffRenameLimit.INFINITY
    val requirements = GitCommitRequirements(shouldIncludeRootChanges(repositoryManager, root), renameLimit,
                                             DiffInMergeCommits.DIFF_TO_PARENTS, false)
    GitDetailsCollector(project, root).readFullDetailsForHashes(hashes, requirements, fast, commitConsumer)
  }

  override fun getSupportedVcs(): VcsKey {
    return GitVcs.getKey()
  }
}
