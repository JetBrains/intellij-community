// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.google.common.graph.Traverser
import com.intellij.collaboration.async.classAsCoroutineName
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.util.getOrNull
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.Disposable
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.filePath
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.remote.hosting.infoFlow
import git4idea.repo.GitRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRChangesService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
import java.util.concurrent.CompletableFuture

internal class GHPRChangesDataProviderImpl(parentCs: CoroutineScope,
                                           private val repositoryDataService: GHPRRepositoryDataService,
                                           private val changesService: GHPRChangesService,
                                           private val pullRequestId: GHPRIdentifier,
                                           private val detailsData: GHPRDetailsDataProviderImpl)
  : GHPRChangesDataProvider, Disposable {
  private val cs = parentCs.childScope(classAsCoroutineName())

  private var lastKnownBaseSha: String? = null
  private var lastKnownHeadSha: String? = null

  init {
    cs.launchNow(Dispatchers.Main) {
      detailsData.detailsComputationFlow.mapNotNull { it.getOrNull() }.collect { details ->
        if (details.baseRefOid != lastKnownBaseSha || details.headRefOid != lastKnownHeadSha) {
          lastKnownBaseSha = details.baseRefOid
          lastKnownHeadSha = details.headRefOid
          reloadChanges()
        }
        else {
          lastKnownBaseSha = details.baseRefOid
          lastKnownHeadSha = details.headRefOid
        }
      }
    }
  }

  private val baseBranchFetchRequestValue = LazyCancellableBackgroundProcessValue.create { indicator ->
    cs.async { detailsData.loadDetails() }.asCompletableFuture().thenCompose {
      changesService.fetchBranch(indicator, it.baseRefName)
    }
  }

  private val headBranchFetchRequestValue = LazyCancellableBackgroundProcessValue.create {
    changesService.fetch(it, "refs/pull/${pullRequestId.number}/head:")
  }

  private val apiCommitsRequestValue = LazyCancellableBackgroundProcessValue.create {
    changesService.loadCommitsFromApi(it, pullRequestId)
  }

  private val changesProviderValue = LazyCancellableBackgroundProcessValue.create { indicator ->
    val commitsRequest = apiCommitsRequestValue.value

    cs.async { detailsData.loadDetails() }.asCompletableFuture()
      .thenCompose {
        changesService.loadMergeBaseOid(indicator, it.baseRefOid, it.headRefOid).thenCombine(commitsRequest) { mergeBaseRef, commits ->
          mergeBaseRef to commits
        }.thenCompose { (mergeBaseRef, commits) ->
          changesService.createChangesProvider(indicator, pullRequestId, it.baseRefOid, mergeBaseRef, it.headRefOid, commits)
        }
      }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override val newChangesInReviewRequest: SharedFlow<Deferred<Boolean>> = run {
    val repository = repositoryDataService.remoteCoordinates.repository
    val currentRevFlow = repository.infoFlow().map { it.currentRevision }
    val headRevFlow = detailsData.detailsComputationFlow.mapNotNull { it.getOrNull() }.map { it.headRefOid }

    // cant just do combineTransform bc it will not cancel previous computation
    currentRevFlow.combine(headRevFlow) { currentRev, headRev ->
      currentRev to headRev
    }.distinctUntilChanged().transformLatest { (currentRev, headRev) ->
      when (currentRev) {
        null -> emit(CompletableDeferred(true))
        headRev -> emit(CompletableDeferred(false))
        else -> supervisorScope {
          val request = async {
            !isAncestor(repository, headRev, currentRev)
          }
          emit(request)
        }
      }
    }.shareIn(cs, SharingStarted.Lazily, 1)
  }

  private suspend fun isAncestor(repository: GitRepository, potentialAncestorRev: String, rev: String): Boolean =
    coroutineToIndicator {
      val h = GitLineHandler(repository.project, repository.root, GitCommand.MERGE_BASE)
      h.setSilent(true)
      h.addParameters("--is-ancestor", potentialAncestorRev, rev)
      Git.getInstance().runCommand(h).success()
    }

  override fun loadChanges() = changesProviderValue.value

  override fun loadPatchFromMergeBase(progressIndicator: ProgressIndicator, commitSha: String, filePath: String)
    : CompletableFuture<FilePatch?> {
    // cache merge base
    return cs.async { detailsData.loadDetails() }.asCompletableFuture().thenCompose {
      changesService.loadMergeBaseOid(progressIndicator, it.baseRefOid, it.headRefOid)
    }.thenCompose {
      changesService.loadPatch(it, commitSha)
    }.thenApplyAsync({ it.find { it.filePath == filePath } }, ProcessIOExecutorService.INSTANCE)
  }

  override fun reloadChanges() {
    baseBranchFetchRequestValue.drop()
    headBranchFetchRequestValue.drop()
    apiCommitsRequestValue.drop()
    changesProviderValue.drop()
  }

  override fun addChangesListener(disposable: Disposable, listener: () -> Unit) =
    changesProviderValue.addDropEventListener(disposable, listener)

  override fun loadCommitsFromApi(): CompletableFuture<List<GHCommit>> = apiCommitsRequestValue.value.thenApply {
    val (lastCommit, graph) = it
    Traverser.forGraph(graph).depthFirstPostOrder(lastCommit).toList()
  }

  override fun addCommitsListener(disposable: Disposable, listener: () -> Unit) =
    apiCommitsRequestValue.addDropEventListener(disposable, listener)

  override fun fetchBaseBranch() = baseBranchFetchRequestValue.value

  override fun fetchHeadBranch() = headBranchFetchRequestValue.value

  override fun dispose() {}
}