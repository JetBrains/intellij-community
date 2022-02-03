// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.diff.chains.AsyncDiffRequestChain
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.comparison.ComparisonManagerImpl
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.NonEmptyActionGroup
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.diff.impl.GenericDataProvider
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.ex.isValidRanges
import com.intellij.openapi.vcs.history.VcsDiffUtil
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupportImpl
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewResolvedThreadsToggleAction
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewThreadsReloadAction
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewThreadsToggleAction
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangesProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.util.DiffRequestChainProducer
import org.jetbrains.plugins.github.util.GHToolbarLabelAction
import java.util.concurrent.CompletableFuture

open class GHPRDiffRequestChainProducer(
  private val project: Project,
  private val dataProvider: GHPRDataProvider,
  private val avatarIconsProvider: GHAvatarIconsProvider,
  private val currentUser: GHUser
) : DiffRequestChainProducer {

  override fun getRequestChain(changes: ListSelection<Change>): DiffRequestChain {
    val changesData = dataProvider.changesData
    val changesProviderFuture = changesData.loadChanges()
    //TODO: check if revisions are already fetched or load via API (could be much quicker in some cases)
    val fetchFuture = CompletableFuture.allOf(changesData.fetchBaseBranch(), changesData.fetchHeadBranch())

    return object : AsyncDiffRequestChain() {
      override fun loadRequestProducers(): ListSelection<out DiffRequestProducer> {
        return changes.map { change ->
          val indicator = ProgressManager.getInstance().progressIndicator
          val changeDataKeys = loadRequestDataKeys(indicator, change, changesProviderFuture, fetchFuture)
          val customDataKeys = createCustomContext(change)

          ChangeDiffRequestProducer.create(project, change, changeDataKeys + customDataKeys)
        }
      }
    }
  }

  protected open fun createCustomContext(change: Change): Map<Key<*>, Any> = emptyMap()

  private fun loadRequestDataKeys(indicator: ProgressIndicator,
                                  change: Change,
                                  changesProviderFuture: CompletableFuture<GHPRChangesProvider>,
                                  fetchFuture: CompletableFuture<Void>): Map<Key<out Any>, Any?> {

    val changesProvider = ProgressIndicatorUtils.awaitWithCheckCanceled(changesProviderFuture, indicator)
    ProgressIndicatorUtils.awaitWithCheckCanceled(fetchFuture, indicator)

    val requestDataKeys = mutableMapOf<Key<out Any>, Any?>()

    VcsDiffUtil.putFilePathsIntoChangeContext(change, requestDataKeys)

    val diffComputer = getDiffComputer(changesProvider, change)
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
        templatePresentation.text = GithubBundle.message("pull.request.diff.view.options")
        templatePresentation.icon = AllIcons.Actions.Show
        add(GHPRDiffReviewThreadsToggleAction())
        add(GHPRDiffReviewResolvedThreadsToggleAction())
      }

      requestDataKeys[DiffUserDataKeys.CONTEXT_ACTIONS] = listOf(
        GHToolbarLabelAction(GithubBundle.message("pull.request.diff.review.label")),
        viewOptionsGroup,
        GHPRDiffReviewThreadsReloadAction(),
        ActionManager.getInstance().getAction("Github.PullRequest.Review.Submit"))
    }
    return requestDataKeys
  }

  private fun getReviewSupport(changesProvider: GHPRChangesProvider, change: Change): GHPRDiffReviewSupport? {
    val diffData = changesProvider.findChangeDiffData(change) ?: return null

    return GHPRDiffReviewSupportImpl(project, dataProvider.reviewData, diffData, avatarIconsProvider, currentUser)
  }

  private fun getDiffComputer(changesProvider: GHPRChangesProvider, change: Change): DiffUserDataKeysEx.DiffComputer? {
    val diffRanges = changesProvider.findChangeDiffData(change)?.diffRangesWithoutContext ?: return null

    return DiffUserDataKeysEx.DiffComputer { text1, text2, policy, innerChanges, indicator ->
      val comparisonManager = ComparisonManagerImpl.getInstanceImpl()
      val lineOffsets1 = LineOffsetsUtil.create(text1)
      val lineOffsets2 = LineOffsetsUtil.create(text2)

      if (!isValidRanges(text1, text2, lineOffsets1, lineOffsets2, diffRanges)) {
        error("Invalid diff line ranges for change $change")
      }
      val iterable = DiffIterableUtil.create(diffRanges, lineOffsets1.lineCount, lineOffsets2.lineCount)
      DiffIterableUtil.iterateAll(iterable).map {
        comparisonManager.compareLinesInner(it.first, text1, text2, lineOffsets1, lineOffsets2, policy, innerChanges,
                                            indicator)
      }.flatten()
    }
  }
}
