// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRStateService
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
import java.util.concurrent.CompletableFuture

class GHPRStateDataProviderImpl(private val stateService: GHPRStateService,
                                private val pullRequestId: GHPRIdentifier,
                                private val detailsData: GHPRDetailsDataProvider)
  : GHPRStateDataProvider, Disposable {

  private var lastKnownBaseBranch: String? = null
  private var lastKnownBaseSha: String? = null
  private var lastKnownHeadSha: String? = null

  init {
    detailsData.addDetailsLoadedListener(this) {
      val details = detailsData.loadedDetails ?: return@addDetailsLoadedListener

      if (lastKnownBaseBranch != null && lastKnownBaseBranch != details.baseRefName) {
        baseBranchProtectionRulesRequestValue.drop()
        reloadMergeabilityState()
      }
      lastKnownBaseBranch = details.baseRefName


      if (lastKnownBaseSha != null && lastKnownBaseSha != details.baseRefOid &&
          lastKnownHeadSha != null && lastKnownHeadSha != details.headRefOid) {
        reloadMergeabilityState()
      }
      lastKnownBaseSha = details.baseRefOid
      lastKnownHeadSha = details.headRefOid
    }
  }

  private val baseBranchProtectionRulesRequestValue = LazyCancellableBackgroundProcessValue.create { indicator ->
    detailsData.loadDetails().thenCompose {
      stateService.loadBranchProtectionRules(indicator, pullRequestId, it.baseRefName)
    }
  }
  private val mergeabilityStateRequestValue = LazyCancellableBackgroundProcessValue.create { indicator ->
    val baseBranchProtectionRulesRequest = baseBranchProtectionRulesRequestValue.value
    detailsData.loadDetails().thenCompose { details ->

      baseBranchProtectionRulesRequest.thenCompose {
        stateService.loadMergeabilityState(indicator, pullRequestId, details.headRefOid, details.url, it)
      }
    }
  }

  override fun loadMergeabilityState(): CompletableFuture<GHPRMergeabilityState> = mergeabilityStateRequestValue.value

  override fun reloadMergeabilityState() = mergeabilityStateRequestValue.drop()

  override fun addMergeabilityStateListener(disposable: Disposable, listener: () -> Unit) =
    mergeabilityStateRequestValue.addDropEventListener(disposable, listener)

  override fun close(progressIndicator: ProgressIndicator): CompletableFuture<Unit> = stateService.close(progressIndicator, pullRequestId)

  override fun reopen(progressIndicator: ProgressIndicator): CompletableFuture<Unit> = stateService.reopen(progressIndicator, pullRequestId)

  override fun merge(progressIndicator: ProgressIndicator, commitMessage: Pair<String, String>, currentHeadRef: String)
    : CompletableFuture<Unit> = stateService.merge(progressIndicator, pullRequestId, commitMessage, currentHeadRef)

  override fun rebaseMerge(progressIndicator: ProgressIndicator, currentHeadRef: String): CompletableFuture<Unit> =
    stateService.rebaseMerge(progressIndicator, pullRequestId, currentHeadRef)

  override fun squashMerge(progressIndicator: ProgressIndicator, commitMessage: Pair<String, String>, currentHeadRef: String)
    : CompletableFuture<Unit> = stateService.squashMerge(progressIndicator, pullRequestId, commitMessage, currentHeadRef)

  override fun dispose() {
    mergeabilityStateRequestValue.drop()
    baseBranchProtectionRulesRequestValue.drop()
  }
}