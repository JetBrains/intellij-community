// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.impl.VcsLogIndexer
import git4idea.GitVcs
import git4idea.history.GitCommitRequirements
import git4idea.history.GitCommitRequirements.DiffInMergeCommits
import git4idea.history.GitCommitRequirements.DiffRenames
import git4idea.history.GitCompressedDetailsCollector
import git4idea.history.GitLogUtil
import git4idea.log.GitLogProvider.*
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMap

class GitLogIndexer(private val project: Project,
                    private val repositoryManager: GitRepositoryManager) : VcsLogIndexer {
  @Throws(VcsException::class)
  override fun readAllFullDetails(root: VirtualFile, encoder: VcsLogIndexer.PathsEncoder,
                                  commitConsumer: com.intellij.util.Consumer<in VcsLogIndexer.CompressedDetails>) {
    val repository = getRepository(repositoryManager, root) ?: return
    val requirements = getGitCommitRequirements(repository)
    GitCompressedDetailsCollector(project, root, encoder).readFullDetails(commitConsumer::consume, requirements, true,
                                                                          *ArrayUtil.toStringArray(GitLogUtil.LOG_ALL))
  }

  @Throws(VcsException::class)
  override fun readFullDetails(root: VirtualFile,
                               hashes: List<String>,
                               encoder: VcsLogIndexer.PathsEncoder,
                               commitConsumer: com.intellij.util.Consumer<in VcsLogIndexer.CompressedDetails>) {
    val repository = getRepository(repositoryManager, root) ?: return
    val requirements = getGitCommitRequirements(repository)
    GitCompressedDetailsCollector(project, root, encoder).readFullDetailsForHashes(hashes, requirements, true, commitConsumer::consume)
  }

  private fun getGitCommitRequirements(repository: GitRepository): GitCommitRequirements {
    val diffRenames = if (Registry.`is`("git.log.index.inexact.renames")) DiffRenames.Limit.Value(RENAME_LIMIT) else DiffRenames.Limit.Exact
    return GitCommitRequirements(shouldIncludeRootChanges(repository), diffRenames, DiffInMergeCommits.DIFF_TO_PARENTS)
  }

  override fun getSupportedVcs(): VcsKey {
    return GitVcs.getKey()
  }

  companion object {
    private const val RENAME_LIMIT = 1
  }
}

class GitCompressedDetails(private val metadata: VcsCommitMetadata,
                           private val changes: List<Int2ObjectMap<Change.Type>>,
                           private val renames: List<Int2IntMap>) : VcsCommitMetadata by metadata, VcsLogIndexer.CompressedDetails {
  override fun getModifiedPaths(parent: Int) = changes[parent]

  override fun getRenamedPaths(parent: Int) = renames[parent]
}
