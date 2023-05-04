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
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.NonEmptyActionGroup
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
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewResolvedThreadsToggleAction
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewThreadsReloadAction
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewThreadsToggleAction
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.util.ChangeDiffRequestProducerFactory
import org.jetbrains.plugins.github.util.DiffRequestChainProducer
import java.util.concurrent.CompletableFuture

open class GHPRDiffRequestChainProducer(
  private val project: Project,
  private val dataProvider: GHPRDataProvider,
  private val htmlImageLoader: AsyncHtmlImageLoader,
  private val avatarIconsProvider: GHAvatarIconsProvider,
  private val repositoryDataService: GHPRRepositoryDataService,
  private val ghostUser: GHUser,
  private val currentUser: GHUser
) : DiffRequestChainProducer {

  internal val changeProducerFactory = ChangeDiffRequestProducerFactory { project, change ->
    val changesData = dataProvider.changesData
    val changesProviderFuture = changesData.loadChanges()
    //TODO: check if revisions are already fetched or load via API (could be much quicker in some cases)
    val fetchFuture = CompletableFuture.allOf(changesData.fetchBaseBranch(), changesData.fetchHeadBranch())

    val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
    val changeDataKeys = loadRequestDataKeys(indicator, change, changesProviderFuture, fetchFuture)
    val customDataKeys = createCustomContext(change)

    ChangeDiffRequestProducer.create(project, change, changeDataKeys + customDataKeys)
  }

  override fun getRequestChain(changes: ListSelection<Change>): DiffRequestChain {
    return object : AsyncDiffRequestChain() {
      override fun loadRequestProducers(): ListSelection<out DiffRequestProducer> {
        return changes.map { change -> changeProducerFactory.create(project, change) }
      }
    }
  }

  protected open fun createCustomContext(change: Change): Map<Key<*>, Any> = emptyMap()

  private fun loadRequestDataKeys(indicator: ProgressIndicator,
                                  change: Change,
                                  changesProviderFuture: CompletableFuture<GitBranchComparisonResult>,
                                  fetchFuture: CompletableFuture<Void>): Map<Key<out Any>, Any?> {

    val changesProvider = ProgressIndicatorUtils.awaitWithCheckCanceled(changesProviderFuture, indicator)
    ProgressIndicatorUtils.awaitWithCheckCanceled(fetchFuture, indicator)

    val requestDataKeys = mutableMapOf<Key<out Any>, Any?>()

    VcsDiffUtil.putFilePathsIntoChangeContext(change, requestDataKeys)

    val diffComputer = changesProvider.patchesByChange[change]?.getDiffComputer()
    if (diffComputer != null) {
      requestDataKeys[DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER] = diffComputer
    }

    val reviewSupport = getReviewSupport(changesProvider, change)
    if (reviewSupport != null) {
      requestDataKeys[GHPRDiffReviewSupport.KEY] = reviewSupport
      requestDataKeys[DiffUserDataKeys.DATA_PROVIDER] = GenericDataProvider().apply {
        putData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER, dataProvider)
        putData(GHPRDiffReviewSupport.DATA_KEY, reviewSupport)
      }
      val viewOptionsGroup = NonEmptyActionGroup().apply {
        isPopup = true
        templatePresentation.text = CollaborationToolsBundle.message("review.diff.toolbar.view.options")
        templatePresentation.icon = AllIcons.Actions.Show
        add(GHPRDiffReviewThreadsToggleAction())
        add(GHPRDiffReviewResolvedThreadsToggleAction())
      }

      requestDataKeys[DiffUserDataKeys.CONTEXT_ACTIONS] = listOf(
        ImmutableToolbarLabelAction(CollaborationToolsBundle.message("review.diff.toolbar.label")),
        viewOptionsGroup,
        GHPRDiffReviewThreadsReloadAction(),
        ActionManager.getInstance().getAction("Github.PullRequest.Review.Submit"))
    }
    return requestDataKeys
  }

  private fun getReviewSupport(changesProvider: GitBranchComparisonResult, change: Change): GHPRDiffReviewSupport? {
    val diffData = changesProvider.patchesByChange[change] ?: return null

    return GHPRDiffReviewSupportImpl(project,
                                     dataProvider.reviewData, dataProvider.detailsData,
                                     htmlImageLoader, avatarIconsProvider,
                                     repositoryDataService,
                                     diffData,
                                     ghostUser,
                                     currentUser)
  }
}
