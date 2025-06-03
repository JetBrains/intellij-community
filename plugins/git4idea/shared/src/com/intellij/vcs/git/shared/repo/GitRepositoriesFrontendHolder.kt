// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.repo

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.util.messages.Topic
import com.intellij.vcs.git.shared.ref.GitCurrentRef
import com.intellij.vcs.git.shared.ref.GitFavoriteRefs
import com.intellij.vcs.git.shared.rpc.GitRepositoryApi
import com.intellij.vcs.git.shared.rpc.GitRepositoryDto
import com.intellij.vcs.git.shared.rpc.GitRepositoryEvent
import com.intellij.vcs.git.shared.rpc.GitRepositoryStateDto
import git4idea.GitStandardLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.GitTag
import git4idea.i18n.GitBundle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.concurrent.ConcurrentHashMap

// This class is temporarily moved to the shared module until the branch widget can be fully moved to the frontend.
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class GitRepositoriesFrontendHolder(
  private val project: Project,
  private val cs: CoroutineScope,
) {
  private val repositories: MutableMap<RepositoryId, GitRepositoryFrontendModelImpl> = ConcurrentHashMap()

  @Volatile
  var initialized: Boolean = false
    private set

  private val initLock = Mutex()

  fun getAll(): List<GitRepositoryFrontendModel> {
    logErrorIfNotInitialized()
    return repositories.values.toList()
  }

  fun get(repositoryId: RepositoryId): GitRepositoryFrontendModel {
    logErrorIfNotInitialized()
    val repo = repositories[repositoryId]
    if (repo != null) return repo

    cs.launch {
      if (fixDesynchronizedRepo(repositoryId)) {
        project.messageBus.syncPublisher(UPDATES).afterUpdate(UpdateType.REPOSITORY_STATE_UPDATED)
      }
    }

    return GitRepositoryFrontendModelStub(repositoryId)
  }

  /**
   * Caller should ensure that [initialized] is set to true before accessing the data in [GitRepositoriesFrontendHolder]
   */
  private fun logErrorIfNotInitialized() {
    if (!initialized) {
      LOG.error("Repositories state holder not initialized", Throwable())
    }
  }

  suspend fun init() {
    if (initialized) return
    initLock.withLock {
      if (initialized) return

      subscribeToRepoEvents()

      initialized = true
    }
  }

  private suspend fun fetchAllRepositories(): Map<RepositoryId, GitRepositoryFrontendModelImpl> = GitRepositoryApi.getInstance().getRepositories(project.projectId()).associate { repositoryDto ->
    repositoryDto.repositoryId to convertToRepositoryInfo(repositoryDto)
  }

  /**
   * @return once the connection is established and [GitRepositoryEvent.InitialState] is received
   */
  private suspend fun subscribeToRepoEvents() {
    val initialized = CompletableDeferred<Unit>()
    cs.childScope("Git repository state synchronization").launch() {
      GitRepositoryApi.getInstance().getRepositoriesEvents(project.projectId()).collect { event ->
        LOG.debug("Received repository event: $event")
        when (event) {
          is GitRepositoryEvent.InitialState -> {
            if (!initialized.isCompleted) {
              repositories.putAll(event.repositories.associate { it.repositoryId to convertToRepositoryInfo(it) })
              initialized.complete(Unit)
            }
          }
          is GitRepositoryEvent.RepositoriesSync -> {
            if (event.repositories.size != repositories.size || !repositories.keys.containsAll(event.repositories)) {
              LOG.warn("State of repositories is not synchronized. " +
                       "Received repositories: ${event.repositories.joinToString { it.toString() }}. " +
                       "Known repositories are: ${repositories.keys.joinToString { it.toString() }}")
              val allRepositories = fetchAllRepositories()
              val reposToRemove = repositories.keys - allRepositories.keys
              repositories.putAll(allRepositories)
              reposToRemove.forEach { repositories.remove(it) }
            } else {
              LOG.debug("Repositories state is synchronized")
            }
          }
          is GitRepositoryEvent.RepositoryCreated -> {
            repositories[event.repository.repositoryId] = convertToRepositoryInfo(event.repository)
          }
          is GitRepositoryEvent.RepositoryDeleted -> repositories.remove(event.repositoryId)
          is GitRepositoryEvent.SingleRepositoryUpdate -> handleSingleRepoUpdate(event)
          GitRepositoryEvent.TagsHidden -> {
            repositories.values.forEach { it.state.tags = emptySet() }
          }
        }

        getUpdateType(event)?.let { project.messageBus.syncPublisher(UPDATES).afterUpdate(it) }
      }
    }
    initialized.await()
  }

  private suspend fun handleSingleRepoUpdate(event: GitRepositoryEvent.SingleRepositoryUpdate) {
    val repoId = event.repositoryId

    val repoInfo = repositories.computeIfPresent(repoId) { _, info ->
      when (event) {
        is GitRepositoryEvent.FavoriteRefsUpdated -> {
          info.favoriteRefs = event.favoriteRefs
        }
        is GitRepositoryEvent.RepositoryStateUpdated -> {
          info.state = convertToRepositoryState(event.newState)
        }
        is GitRepositoryEvent.TagsLoaded -> {
          info.state.tags = event.tags
        }
      }

      info
    }

    if (repoInfo == null) {
      fixDesynchronizedRepo(repoId)
    }
  }

  private suspend fun fixDesynchronizedRepo(repoId: RepositoryId): Boolean {
    LOG.warn("State of repository $repoId is not synchronized. " +
             "Known repositories are: ${repositories.keys.joinToString { it.toString() }}")
    val repositoryDto = GitRepositoryApi.getInstance().getRepository(repoId)
    if (repositoryDto != null) {
      repositories[repoId] = convertToRepositoryInfo(repositoryDto)
    }
    else {
      LOG.warn("Failed to fetch repository status $repoId")
    }
    return repositoryDto != null
  }

  companion object {
    fun getInstance(project: Project): GitRepositoriesFrontendHolder = project.getService(GitRepositoriesFrontendHolder::class.java)

    @ApiStatus.Internal
    val UPDATES: Topic<UpdatesListener> = Topic(UpdatesListener::class.java, Topic.BroadcastDirection.NONE)

    private val LOG = Logger.getInstance(GitRepositoriesFrontendHolder::class.java)

    private fun convertToRepositoryInfo(repositoryDto: GitRepositoryDto) =
      GitRepositoryFrontendModelImpl(
        repositoryId = repositoryDto.repositoryId,
        shortName = repositoryDto.shortName,
        state = convertToRepositoryState(repositoryDto.state),
        favoriteRefs = repositoryDto.favoriteRefs,
        rootFileId = repositoryDto.root,
      )

    private fun convertToRepositoryState(repositoryStateDto: GitRepositoryStateDto) =
      GitRepositoryStateImpl(
        currentRef = repositoryStateDto.currentRef,
        revision = repositoryStateDto.revision,
        localBranches = repositoryStateDto.localBranches,
        remoteBranches = repositoryStateDto.remoteBranches,
        tags = repositoryStateDto.tags,
        recentBranches = repositoryStateDto.recentBranches,
        operationState = repositoryStateDto.operationState,
        trackingInfo = repositoryStateDto.trackingInfo,
      )

    private fun getUpdateType(rpcEvent: GitRepositoryEvent): UpdateType? = when (rpcEvent) {
      is GitRepositoryEvent.FavoriteRefsUpdated -> UpdateType.FAVORITE_REFS_UPDATED
      is GitRepositoryEvent.RepositoryCreated -> UpdateType.REPOSITORY_CREATED
      is GitRepositoryEvent.RepositoryDeleted -> UpdateType.REPOSITORY_DELETED
      is GitRepositoryEvent.RepositoryStateUpdated -> UpdateType.REPOSITORY_STATE_UPDATED
      GitRepositoryEvent.TagsHidden -> UpdateType.TAGS_HIDDEN
      is GitRepositoryEvent.TagsLoaded -> UpdateType.TAGS_LOADED
      is GitRepositoryEvent.RepositoriesSync, is GitRepositoryEvent.InitialState -> null
    }
  }

  @ApiStatus.Internal
  fun interface UpdatesListener {
    fun afterUpdate(updateType: UpdateType)
  }

  @ApiStatus.Internal
  enum class UpdateType {
    REPOSITORY_CREATED, REPOSITORY_DELETED, FAVORITE_REFS_UPDATED, REPOSITORY_STATE_UPDATED, TAGS_LOADED, TAGS_HIDDEN
  }
}

private open class GitRepositoryFrontendModelImpl(
  override val repositoryId: RepositoryId,
  override val shortName: String,
  override var state: GitRepositoryStateImpl,
  override var favoriteRefs: GitFavoriteRefs,
  private val rootFileId: VirtualFileId,
) : GitRepositoryFrontendModel {
  override val root: VirtualFile?
    get() = rootFileId.virtualFile()
}

private class GitRepositoryStateImpl(
  override val currentRef: GitCurrentRef?,
  override val revision: @NlsSafe GitHash?,
  override val localBranches: Set<GitStandardLocalBranch>,
  override val remoteBranches: Set<GitStandardRemoteBranch>,
  override var tags: Set<GitTag>,
  override val recentBranches: List<GitStandardLocalBranch>,
  override val operationState: GitOperationState,
  private val trackingInfo: Map<String, GitStandardRemoteBranch>,
) : GitRepositoryState {
  override fun getTrackingInfo(branch: GitStandardLocalBranch): GitStandardRemoteBranch? = trackingInfo[branch.name]

  override fun getDisplayableBranchText(): @Nls String {
    val branchOrEmpty = currentBranch?.name ?: ""
    return when (operationState) {
      GitOperationState.NORMAL -> branchOrEmpty
      GitOperationState.REBASE -> GitBundle.message("git.status.bar.widget.text.rebase", branchOrEmpty)
      GitOperationState.MERGE -> GitBundle.message("git.status.bar.widget.text.merge", branchOrEmpty)
      GitOperationState.CHERRY_PICK -> GitBundle.message("git.status.bar.widget.text.cherry.pick", branchOrEmpty)
      GitOperationState.REVERT -> GitBundle.message("git.status.bar.widget.text.revert", branchOrEmpty)
      GitOperationState.DETACHED_HEAD -> getDetachedHeadDisplayableText()
    }
  }

  private fun getDetachedHeadDisplayableText(): @Nls String =
    if (currentRef is GitCurrentRef.Tag) currentRef.tag.name
    else revision?.hash ?: GitBundle.message("git.status.bar.widget.text.unknown")
}

private class GitRepositoryFrontendModelStub(override val repositoryId: RepositoryId) : GitRepositoryFrontendModel {
  override val shortName = ""
  override val state = GitRepositoryStateImpl(null, null, emptySet(), emptySet(), emptySet(),  emptyList(), GitOperationState.DETACHED_HEAD, emptyMap())
  override val favoriteRefs = GitFavoriteRefs(emptySet(), emptySet(), emptySet())
  override val root = null
}
