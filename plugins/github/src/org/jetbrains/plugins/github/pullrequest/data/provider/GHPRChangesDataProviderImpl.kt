// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.google.common.graph.Traverser
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.Disposable
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.progress.ProgressIndicator
import git4idea.changes.filePath
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRChangesService
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
import java.util.concurrent.CompletableFuture

class GHPRChangesDataProviderImpl(private val changesService: GHPRChangesService,
                                  private val pullRequestId: GHPRIdentifier,
                                  private val detailsData: GHPRDetailsDataProviderImpl)
  : GHPRChangesDataProvider, Disposable {

  private var lastKnownBaseSha: String? = null
  private var lastKnownHeadSha: String? = null

  init {
    detailsData.addDetailsLoadedListener(this) {
      val details = detailsData.loadedDetails ?: return@addDetailsLoadedListener

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

  private val baseBranchFetchRequestValue = LazyCancellableBackgroundProcessValue.create { indicator ->
    detailsData.loadDetails().thenCompose {
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

    detailsData.loadDetails()
      .thenCompose {
        changesService.loadMergeBaseOid(indicator, it.baseRefOid, it.headRefOid).thenCombine(commitsRequest) { mergeBaseRef, commits ->
          mergeBaseRef to commits
        }.thenCompose { (mergeBaseRef, commits) ->
          changesService.createChangesProvider(indicator, it.baseRefOid, mergeBaseRef, it.headRefOid, commits)
        }
      }
  }

  override fun loadChanges() = changesProviderValue.value

  override fun loadPatchFromMergeBase(progressIndicator: ProgressIndicator, commitSha: String, filePath: String)
    : CompletableFuture<FilePatch?> {
    // cache merge base
    return detailsData.loadDetails().thenCompose {
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