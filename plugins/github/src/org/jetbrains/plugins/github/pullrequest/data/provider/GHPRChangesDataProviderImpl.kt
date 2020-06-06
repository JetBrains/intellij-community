// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.Disposable
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRChangesService
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue

class GHPRChangesDataProviderImpl(private val changesService: GHPRChangesService,
                                  private val pullRequestId: GHPRIdentifier,
                                  private val detailsData: GHPRDetailsDataProviderImpl)
  : GHPRChangesDataProvider, Disposable {

  private var lastKnownBaseSha: String? = null
  private var lastKnownHeadSha: String? = null

  init {
    detailsData.addDetailsLoadedListener(this) {
      val details = detailsData.loadedDetails ?: return@addDetailsLoadedListener

      if (lastKnownBaseSha != null && lastKnownBaseSha != details.baseRefOid &&
          lastKnownHeadSha != null && lastKnownHeadSha != details.headRefOid) {
        reloadChanges()
      }
      lastKnownBaseSha = details.baseRefOid
      lastKnownHeadSha = details.headRefOid
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
        changesService.loadMergeBaseOid(indicator, it.baseRefOid, it.headRefOid)
      }.thenCompose { mergeBase ->
        commitsRequest.thenCompose {
          changesService.createChangesProvider(indicator, mergeBase, it)
        }
      }
  }

  override fun loadChanges() = changesProviderValue.value

  override fun reloadChanges() {
    baseBranchFetchRequestValue.drop()
    headBranchFetchRequestValue.drop()
    apiCommitsRequestValue.drop()
    changesProviderValue.drop()
  }

  override fun addChangesListener(disposable: Disposable, listener: () -> Unit) =
    changesProviderValue.addDropEventListener(disposable, listener)

  override fun loadCommitsFromApi() = apiCommitsRequestValue.value

  override fun addCommitsListener(disposable: Disposable, listener: () -> Unit) =
    apiCommitsRequestValue.addDropEventListener(disposable, listener)

  override fun fetchBaseBranch() = baseBranchFetchRequestValue.value

  override fun fetchHeadBranch() = headBranchFetchRequestValue.value

  override fun dispose() {}
}