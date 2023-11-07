// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.action.ImmutableToolbarLabelAction
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.diff.chains.AsyncDiffRequestChain
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.diff.impl.GenericDataProvider
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.history.VcsDiffUtil
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.getDiffComputer
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupportImpl
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewThreadsReloadAction
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.util.DiffRequestChainProducer
import java.util.concurrent.CompletableFuture

internal class GHPRDiffRequestChainProducer(
  private val project: Project,
  private val dataProvider: GHPRDataProvider,
  private val htmlImageLoader: AsyncHtmlImageLoader,
  private val avatarIconsProvider: GHAvatarIconsProvider,
  private val repositoryDataService: GHPRRepositoryDataService,
  private val ghostUser: GHUser,
  private val currentUser: GHUser,
  private val createCustomContext: (Change) -> Map<Key<*>, Any>
) : DiffRequestChainProducer {

  override fun getRequestChain(changes: ListSelection<Change>): DiffRequestChain =
    object : AsyncDiffRequestChain() {
      override fun loadRequestProducers(): ListSelection<out DiffRequestProducer> =
        changes.map { change -> createDiffRequestProducer(project, change) }
    }

  private fun createDiffRequestProducer(project: Project, change: Change): DiffRequestProducer? {
    val changesData = dataProvider.changesData
    val changesProviderFuture = changesData.loadChanges()
    //TODO: check if revisions are already fetched or load via API (could be much quicker in some cases)
    val fetchFuture = CompletableFuture.allOf(changesData.fetchBaseBranch(), changesData.fetchHeadBranch())

    val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
    val changeDataKeys = createData(this, change, loadBranchComparisonResult(changesProviderFuture, indicator, fetchFuture))
    val customDataKeys = createCustomContext(change)

    return ChangeDiffRequestProducer.create(project, change, changeDataKeys + customDataKeys)
  }

  companion object {
    private fun createData(producer: GHPRDiffRequestChainProducer, change: Change,
                           changesProvider: GitBranchComparisonResult): Map<Key<out Any>, Any?> {
      val requestDataKeys = mutableMapOf<Key<out Any>, Any?>()
      VcsDiffUtil.putFilePathsIntoChangeContext(change, requestDataKeys)

      installDiffComputer(changesProvider, change, requestDataKeys)

      val reviewSupport = createReviewSupport(producer, changesProvider, change)
      if (reviewSupport != null) {
        installReviewSupport(requestDataKeys, reviewSupport, producer.dataProvider)
        requestDataKeys[DiffUserDataKeys.CONTEXT_ACTIONS] = listOf(
          ImmutableToolbarLabelAction(CollaborationToolsBundle.message("review.diff.toolbar.label")),
          GHPRDiffReviewThreadsReloadAction(),
          ActionManager.getInstance().getAction("Github.PullRequest.Review.Submit"))
      }
      return requestDataKeys
    }

    private fun loadBranchComparisonResult(changesProviderFuture: CompletableFuture<GitBranchComparisonResult>,
                                          indicator: ProgressIndicator,
                                          fetchFuture: CompletableFuture<Void>): GitBranchComparisonResult {
      val changesProvider = ProgressIndicatorUtils.awaitWithCheckCanceled(changesProviderFuture, indicator)
      ProgressIndicatorUtils.awaitWithCheckCanceled(fetchFuture, indicator)
      return changesProvider
    }

    private fun createReviewSupport(producer: GHPRDiffRequestChainProducer, changesProvider: GitBranchComparisonResult, change: Change) =
      createReviewSupport(producer.project, changesProvider, change, producer.dataProvider, producer.htmlImageLoader,
                          producer.avatarIconsProvider,
                          producer.repositoryDataService,
                          producer.ghostUser,
                          producer.currentUser)
  }
}

internal fun createReviewSupport(project: Project,
                                 changesProvider: GitBranchComparisonResult,
                                 change: Change,
                                 dataProvider: GHPRDataProvider,
                                 imageLoader: AsyncHtmlImageLoader,
                                 iconsProvider: GHAvatarIconsProvider,
                                 repositoryDataService: GHPRRepositoryDataService,
                                 ghostUser: GHUser,
                                 currentUser: GHUser
): GHPRDiffReviewSupport? {
  val diffData = changesProvider.patchesByChange[change] ?: return null

  return GHPRDiffReviewSupportImpl(project,
                                   dataProvider.reviewData, dataProvider.detailsData,
                                   imageLoader,
                                   iconsProvider,
                                   repositoryDataService,
                                   diffData,
                                   ghostUser,
                                   currentUser)
}

internal fun installDiffComputer(changesProvider: GitBranchComparisonResult,
                                change: Change,
                                requestDataKeys: MutableMap<Key<out Any>, Any?>) {
  val diffComputer = changesProvider.patchesByChange[change]?.getDiffComputer() ?: return
  requestDataKeys[DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER] = diffComputer
}

internal fun installReviewSupport(keys: MutableMap<Key<out Any>, Any?>,
                                 reviewSupport: GHPRDiffReviewSupport,
                                 dataProvider: GHPRDataProvider) {
  keys[GHPRDiffReviewSupport.KEY] = reviewSupport
  keys[DiffUserDataKeys.DATA_PROVIDER] = GenericDataProvider().apply {
    putData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER, dataProvider)
    putData(GHPRDiffReviewSupport.DATA_KEY, reviewSupport)
  }
}