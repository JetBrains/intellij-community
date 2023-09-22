// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.create.model

import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitPushUtil
import git4idea.GitRemoteBranch
import git4idea.history.GitLogUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

internal interface GitLabMergeRequestCreateViewModel {
  val projectsManager: GitLabProjectsManager
  val projectData: GitLabProject

  val isBusy: Flow<Boolean>

  val branchState: Flow<BranchState?>

  val commits: SharedFlow<Result<List<VcsCommitMetadata>>?>

  fun updateTitle(text: String)
  fun updateBranchState(state: BranchState?)

  fun createMergeRequest()
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabMergeRequestCreateViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  override val projectsManager: GitLabProjectsManager,
  override val projectData: GitLabProject,
  private val onMergeRequestCreated: suspend (mrIid: String) -> Unit
) : GitLabMergeRequestCreateViewModel {
  private val cs: CoroutineScope = parentCs.childScope()
  private val taskLauncher = SingleCoroutineLauncher(cs)

  override val isBusy: Flow<Boolean> = taskLauncher.busy

  private val _branchState: MutableStateFlow<BranchState?> = MutableStateFlow(null)
  override val branchState: Flow<BranchState?> = _branchState.asSharedFlow()

  override val commits: SharedFlow<Result<List<VcsCommitMetadata>>?> = branchState.transformLatest { model ->
    if (model == null) {
      emit(Result.success(emptyList()))
      return@transformLatest
    }

    emit(null)
    val result = withContext(Dispatchers.IO) {
      runCatchingUser {
        GitLogUtil.collectMetadata(
          project,
          model.headRepo.gitRepository.root,
          "${model.baseBranch.name}..${model.headBranch.name}"
        ).commits
      }
    }
    emit(result)
  }.modelFlow(cs, thisLogger())

  private val creatingError: MutableStateFlow<Throwable?> = MutableStateFlow(null)

  private val title: MutableStateFlow<String> = MutableStateFlow("")

  init {
    cs.launch {
      val baseRepo = projectData.projectMapping
      val baseGitRepo = baseRepo.gitRepository
      val defaultBranch = projectData.defaultBranch.await()
      val baseBranch = baseGitRepo.getBranchTrackInfo(defaultBranch)?.remoteBranch ?: return@launch
      val currentBranch = baseGitRepo.currentBranch ?: return@launch

      _branchState.value = BranchState(baseRepo, baseBranch, baseRepo, currentBranch)
    }
  }

  override fun updateTitle(text: String) {
    title.value = text
  }

  override fun updateBranchState(state: BranchState?) {
    _branchState.value = state
  }

  override fun createMergeRequest() {
    taskLauncher.launch {
      val (_, baseBranch, headRepo, headBranch) = _branchState.value ?: return@launch
      try {
        val gitRemoteBranch = findGitRemoteBranch(headRepo, headBranch)
        val mergeRequest = projectData.createMergeRequest(
          sourceBranch = gitRemoteBranch.nameForRemoteOperations,
          targetBranch = baseBranch.nameForRemoteOperations,
          title = title.value.ifBlank { gitRemoteBranch.nameForRemoteOperations }
        )
        onMergeRequestCreated(mergeRequest.iid)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        creatingError.value = e
      }
    }
  }

  private suspend fun findGitRemoteBranch(headRepo: GitLabProjectMapping, headBranch: GitBranch): GitRemoteBranch {
    return withContext(Dispatchers.Main) {
      when (headBranch) {
        is GitRemoteBranch -> headBranch
        is GitLocalBranch -> {
          val dialogMessages = GitPushUtil.BranchNameInputDialogMessages(
            GitLabBundle.message("merge.request.create.branch.dialog.title"),
            GitLabBundle.message("merge.request.create.branch.dialog.message"),
            GitLabBundle.message("merge.request.create.branch.dialog.comment", headBranch.name, headRepo.gitRemote.name)
          )
          GitPushUtil.findOrPushRemoteBranch(
            project, EmptyProgressIndicator(),
            headRepo.gitRepository, headRepo.gitRemote, headBranch,
            dialogMessages
          ).await()
        }
        else -> error("No more inheritances")
      }
    }
  }
}

internal data class BranchState(
  val baseRepo: GitLabProjectMapping,
  val baseBranch: GitRemoteBranch,
  val headRepo: GitLabProjectMapping,
  val headBranch: GitBranch
) {
  companion object {
    internal fun fromDirectionModel(directionModel: GitLabMergeRequestCreateDirectionModel): BranchState? = with(directionModel) {
      BranchState(
        baseRepo,
        baseBranch ?: return null,
        headRepo ?: return null,
        headBranch ?: return null,
      )
    }
  }
}