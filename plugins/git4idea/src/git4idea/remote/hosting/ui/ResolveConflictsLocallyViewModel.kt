// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting.ui

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.Either
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.childScope
import git4idea.GitRemoteBranch
import git4idea.GitStandardRemoteBranch
import git4idea.branch.GitBrancher
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.remote.hosting.HostedGitRepositoryRemote
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class ResolveConflictsMethod {
  REBASE,
  MERGE;
}

@ApiStatus.Internal
interface ResolveConflictsLocallyViewModel<Error : Any> {
  /**
   * Whether there are conflicts that need to be resolved before merging.
   *
   * If the value is `null`, there is a check currently in progress or there is something
   * else preventing us from knowing whether there are conflicts.
   */
  val hasConflicts: StateFlow<Boolean?>

  val requestOrError: StateFlow<Either<Error, ResolveConflictsLocallyCoordinates>>

  val isBusy: StateFlow<Boolean>

  fun performResolveConflicts(chooseMethod: suspend () -> ResolveConflictsMethod?)
}

@ApiStatus.Internal
abstract class BaseResolveConflictsLocallyViewModel<Error : Any>(
  parentCs: CoroutineScope,
  private val project: Project,
  private val gitRepository: GitRepository,
) : ResolveConflictsLocallyViewModel<Error> {
  protected val cs = parentCs.childScope("Resolve Conflicts Locally Scope")
  private val taskLauncher = SingleCoroutineLauncher(cs.childScope("Resolve Conflicts Locally Task"))

  private val requestFlow: StateFlow<ResolveConflictsLocallyCoordinates?>
    get() = requestOrError.mapState { it.asRightOrNull() }

  private val repositories = listOf(gitRepository)

  private val _isBusyFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override val isBusy: StateFlow<Boolean> = taskLauncher.busy

  override fun performResolveConflicts(chooseMethod: suspend () -> ResolveConflictsMethod?) {
    taskLauncher.launch {
      val method = chooseMethod()
      when (method) {
        ResolveConflictsMethod.REBASE -> rebase()
        ResolveConflictsMethod.MERGE -> merge()
        else -> {}
      }
    }
  }

  private suspend fun rebase() {
    prepareAndPerformUpdate { brancher, baseBranch ->
      brancher.rebase(repositories, baseBranch)
    }
  }

  private suspend fun merge() {
    prepareAndPerformUpdate { brancher, baseBranch ->
      brancher.merge(baseBranch, GitBrancher.DeleteOnMergeOption.NOTHING, repositories, false)
    }
  }

  private suspend fun prepareAndPerformUpdate(updater: suspend (brancher: GitBrancher, baseBranch: GitRemoteBranch) -> Unit) {
    val request = requestFlow.value ?: return

    val headRemote = getOrCreateRemote(request.headRemoteDescriptor) ?: return
    val headBranch = GitStandardRemoteBranch(headRemote, request.headRefName)

    val baseRemote = getOrCreateRemote(request.baseRemoteDescriptor) ?: return
    val baseBranch = GitStandardRemoteBranch(baseRemote, request.baseRefName)

    // Fetch base ref
    val fetcher = GitFetchSupport.fetchSupport(project)
    withContext(Dispatchers.IO) {
      withBackgroundProgress(project, GitBundle.message("git.fetch.progress")) {
        fetcher.fetch(gitRepository, baseRemote, baseBranch.nameForRemoteOperations)
      }
    }

    // Checkout the head branch
    withContext(Dispatchers.Main) {
      GitRemoteBranchesUtil.checkoutRemoteBranch(gitRepository, headBranch)
    }

    // Rebase or merge on the base ref
    val brancher = GitBrancher.getInstance(project)

    return updater(brancher, baseBranch)
  }

  private suspend fun getOrCreateRemote(remoteDescriptor: HostedGitRepositoryRemote): GitRemote? =
    GitRemoteBranchesUtil.findOrCreateRemote(gitRepository, remoteDescriptor)
}