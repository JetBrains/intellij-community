// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.diff

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.diff.impl.DiffRevisionMetadataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.GitContentRevision
import git4idea.GitUtil
import git4idea.history.GitLogUtil
import git4idea.index.vfs.filePath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import java.time.Duration
import java.util.*
import kotlin.jvm.optionals.getOrNull

internal class GitDiffRevisionMetadataProvider : DiffRevisionMetadataProvider {
  override fun canApply(contentRevision: ContentRevision): Boolean = contentRevision is GitContentRevision

  override suspend fun getMetadata(project: Project, contentRevision: ContentRevision): VcsCommitMetadata? =
    GitDiffRevisionMetadataInProject.getInstance(project).get(contentRevision.revisionNumber.asString(), contentRevision.file)
}

@Service(Service.Level.PROJECT)
internal data class GitDiffRevisionMetadataInProject(val project: Project, val scope: CoroutineScope) {
  private val cache: AsyncLoadingCache<CommitInRepo, Optional<VcsCommitMetadata>> = Caffeine.newBuilder()
    .expireAfterAccess(EXPIRE_IN)
    .executor(AppExecutorUtil.getAppExecutorService())
    .buildAsync { key, executor -> scope.future { loadMetadata(key) } }

  suspend fun get(revision: String, file: FilePath): VcsCommitMetadata? {
    if (!GitUtil.isHashString(revision)) return null
    val root = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(file)?.filePath() ?: return null
    return cache.get(CommitInRepo(revision, root)).await().getOrNull()
  }

  private fun loadMetadata(commitInRepo: CommitInRepo): Optional<VcsCommitMetadata> {
    LOG.trace("Loading metadata for ${commitInRepo.hash} in ${commitInRepo.repoRoot.path}")

    val root = commitInRepo.repoRoot.virtualFile ?: return Optional.empty()
    val commitMetadata = GitLogUtil.collectMetadata(project, root, listOf(commitInRepo.hash)).singleOrNull()
    return Optional.ofNullable(commitMetadata)
  }

  private data class CommitInRepo(val hash: String, val repoRoot: FilePath)

  companion object {
    private val EXPIRE_IN = Duration.ofMinutes(1)
    private val LOG = thisLogger()

    fun getInstance(project: Project): GitDiffRevisionMetadataInProject = project.getService(GitDiffRevisionMetadataInProject::class.java)
  }
}