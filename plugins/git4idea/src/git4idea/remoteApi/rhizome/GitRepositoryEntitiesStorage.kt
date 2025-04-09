// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi.rhizome

import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.project.asEntityOrNull
import com.intellij.vcs.git.shared.isRdBranchWidgetEnabled
import com.intellij.vcs.git.shared.ref.GitRefPrefix
import com.intellij.vcs.git.shared.rhizome.repository.GitRepositoryEntity
import com.intellij.vcs.git.shared.rhizome.repository.GitRepositoryFavoriteRefsEntity
import com.intellij.vcs.git.shared.rhizome.repository.GitRepositoryStateEntity
import com.intellij.vcs.git.shared.rpc.GitReferencesSet
import com.intellij.vcsUtil.VcsUtil
import com.jetbrains.rhizomedb.entities
import com.jetbrains.rhizomedb.entity
import fleet.kernel.SharedChangeScope
import fleet.kernel.change
import fleet.kernel.shared
import git4idea.GitStandardRemoteBranch
import git4idea.branch.GitBranchType
import git4idea.branch.GitTagType
import git4idea.remoteApi.rpcId
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.tree.tags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture

@Service(Service.Level.PROJECT)
internal class GitRepositoryEntitiesStorage(private val project: Project, private val cs: CoroutineScope) {
  suspend fun cleanupIfNeeded() {
    if (!Registry.isRdBranchWidgetEnabled()) return

    val projectEntity = project.asEntityOrNull() ?: return
    val repositories = GitRepositoryManager.getInstance(project).repositories.map { it.rpcId() }

    val reposToDelete = entities(GitRepositoryEntity.Project, projectEntity).filter { it.repositoryId !in repositories }
    if (reposToDelete.isEmpty()) return

    LOG.info("Deleting ${reposToDelete.size} repositories entities")
    change {
      shared {
        reposToDelete.forEach { it.delete() }
      }
    }
  }

  fun runRepoSync(gitRepository: GitRepository, afterCreation: Boolean): CompletableFuture<Unit> = cs.launch {
    syncRepo(gitRepository, afterCreation = afterCreation)
  }.asCompletableFuture()

  private suspend fun syncRepo(gitRepository: GitRepository, afterCreation: Boolean) {
    val refsSet = GitReferencesSet(
      gitRepository.info.localBranchesWithHashes.keys,
      gitRepository.info.remoteBranchesWithHashes.keys.filterIsInstance<GitStandardRemoteBranch>().toSet(),
      gitRepository.tags.keys,
    )
    val currentRef = gitRepository.info.currentBranch?.fullName

    val favoriteRefsToInsert = if (afterCreation) getFavoriteRefs(gitRepository) else null

    if (LOG.isDebugEnabled) {
      val refsString = "${refsSet.localBranches.size} local branches, ${refsSet.remoteBranches.size} remote branches, ${refsSet.tags.size} tags"
      LOG.debug("Syncing repository entity for ${gitRepository.root}\n" +
                "Current ref: $currentRef\n" +
                "Refs: $refsString\n" +
                "Favorite refs: ${favoriteRefsToInsert?.size ?: "<skipped>"}")
    }
    change {
      shared {
        updateRepoTx(gitRepository, currentRef, refsSet, favoriteRefsToInsert)
      }
    }
  }

  suspend fun updateFavoriteRefs(gitRepository: GitRepository?) {
    if (!Registry.isRdBranchWidgetEnabled()) return

    LOG.info("Updating favorite refs for ${gitRepository?.root ?: "all git repos"}")

    val refsToInsert = mutableMapOf<GitRepositoryEntity, Set<String>>()
    if (gitRepository != null) {
      val repoEntity = entity(GitRepositoryEntity.RepositoryIdValue, gitRepository.rpcId()) ?: return
      refsToInsert[repoEntity] = getFavoriteRefs(gitRepository)
    }
    else {
      val projectEntity = project.asEntityOrNull() ?: return
      entities(GitRepositoryEntity.Project, projectEntity).forEach { repoEntity ->
        val repo = GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(repoEntity.repositoryId.rootPath.virtualFile())
                   ?: return@forEach
        refsToInsert[repoEntity] = getFavoriteRefs(repo)
      }
    }
    change {
      shared {
        refsToInsert.forEach { (repoEntity, refsToInsert) ->
          updateFavoriteRefsTx(repoEntity, refsToInsert)
        }
      }
    }
  }

  private fun SharedChangeScope.updateRepoTx(
    gitRepository: GitRepository,
    currentRef: String?,
    refs: GitReferencesSet,
    favoriteRefsToInsert: Set<String>?,
  ) {
    val projectEntity = project.asEntityOrNull() ?: return

    val repositoryId = gitRepository.rpcId()

    val existingRepo = entity(GitRepositoryEntity.RepositoryIdValue, gitRepository.rpcId())
    val previousState = existingRepo?.state
    val previousFavoriteRefs = existingRepo?.favoriteRefs

    val newStateEntity = GitRepositoryStateEntity.new {
      it[GitRepositoryStateEntity.RepositoryIdValue] = repositoryId
      it[GitRepositoryStateEntity.CurrentRef] = currentRef
      it[GitRepositoryStateEntity.ReferencesSet] = refs
      it[GitRepositoryStateEntity.RecentBranches] = gitRepository.branches.recentCheckoutBranches
    }
    val newFavoriteRefs = favoriteRefsToInsert?.let {
      GitRepositoryFavoriteRefsEntity.new {
        it[GitRepositoryFavoriteRefsEntity.RepositoryIdValue] = repositoryId
        it[GitRepositoryFavoriteRefsEntity.FavoriteRefs] = favoriteRefsToInsert
      }
    }

    GitRepositoryEntity.upsert(GitRepositoryEntity.RepositoryIdValue, repositoryId) {
      it[GitRepositoryEntity.Project] = projectEntity
      it[GitRepositoryEntity.State] = newStateEntity
      it[GitRepositoryEntity.ShortName] = VcsUtil.getShortVcsRootName(project, gitRepository.root)
      if (newFavoriteRefs != null) {
        it[GitRepositoryEntity.FavoriteRefs] = newFavoriteRefs
      }
    }

    previousState?.delete()
    if (newFavoriteRefs != null) {
      previousFavoriteRefs?.delete()
    }
  }


  private fun SharedChangeScope.updateFavoriteRefsTx(repositoryEntity: GitRepositoryEntity, favoriteRefsToInsert: Set<String>) {
    val oldFavoriteRefs = repositoryEntity.favoriteRefs

    val newFavoriteRefs = GitRepositoryFavoriteRefsEntity.new {
      it[GitRepositoryFavoriteRefsEntity.RepositoryIdValue] = repositoryEntity.repositoryId
      it[GitRepositoryFavoriteRefsEntity.FavoriteRefs] = favoriteRefsToInsert
    }
    repositoryEntity.update {
      it[GitRepositoryEntity.FavoriteRefs] = newFavoriteRefs
    }

    oldFavoriteRefs.delete()
  }

  private fun getFavoriteRefs(gitRepository: GitRepository): Set<String> {
    val branchManager = project.service<GitBranchManager>()
    return listOf(
        GitBranchType.REMOTE to GitRefPrefix.REMOTES,
        GitBranchType.LOCAL to GitRefPrefix.HEADS,
        GitTagType to GitRefPrefix.TAGS
      ).flatMapTo(mutableSetOf()) { (type, prefix) ->
        branchManager.getFavoriteRefs(type, gitRepository).map(prefix::append)
      }
  }

  internal class VcsMappingListener(private val project: Project, private val cs: CoroutineScope) : VcsRepositoryMappingListener {
    override fun mappingChanged() {
      if (!Registry.isRdBranchWidgetEnabled()) return

      cs.launch {
        getInstance(project).cleanupIfNeeded()
      }
    }
  }

  companion object {
    private val LOG = logger<GitRepositoryEntitiesStorage>()

    @JvmStatic
    fun getInstance(project: Project): GitRepositoryEntitiesStorage = project.service()
  }
}
