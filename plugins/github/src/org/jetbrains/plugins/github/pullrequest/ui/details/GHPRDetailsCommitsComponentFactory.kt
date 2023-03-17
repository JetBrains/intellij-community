// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.details.CommitPresenter
import com.intellij.collaboration.ui.codereview.details.CommitRenderer
import com.intellij.collaboration.ui.codereview.details.SelectableWrapper
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil
import com.intellij.collaboration.ui.util.bindDisabled
import com.intellij.collaboration.ui.util.bindText
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.ui.popup.PopupState
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRCommitsViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRDiffController
import java.awt.FontMetrics
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants

internal object GHPRDetailsCommitsComponentFactory {
  private const val COMPONENTS_GAP = 4
  private const val COMMIT_HASH_OFFSET = 8

  fun create(scope: CoroutineScope, commitsVm: GHPRCommitsViewModel, diffBridge: GHPRDiffController): JComponent {
    val commitsPopupTitle = JLabel().apply {
      font = JBFont.regular().asBold()
      bindText(scope, commitsVm.reviewCommits.map { commits ->
        CollaborationToolsBundle.message("review.details.commits.title.text", commits.size)
      })
    }
    val commitsPopup = createCommitChooserActionLink(scope, commitsVm, diffBridge)
    val nextCommitIcon = InlineIconButton(AllIcons.Chooser.Bottom).apply {
      withBackgroundHover = true
      actionListener = ActionListener { commitsVm.selectNextCommit() }
      bindVisibility(scope, commitsVm.selectedCommit.map { it != null })
      bindDisabled(scope, combine(commitsVm.selectedCommitIndex, commitsVm.reviewCommits) { selectedCommitIndex, commits ->
        selectedCommitIndex == commits.size - 1
      })
    }
    val previousCommitIcon = InlineIconButton(AllIcons.Chooser.Top).apply {
      withBackgroundHover = true
      actionListener = ActionListener { commitsVm.selectPreviousCommit() }
      bindVisibility(scope, commitsVm.selectedCommit.map { it != null })
      bindDisabled(scope, commitsVm.selectedCommitIndex.map { it == 0 })
    }

    return HorizontalListPanel(COMPONENTS_GAP).apply {
      add(commitsPopupTitle)
      add(commitsPopup)
      add(nextCommitIcon)
      add(previousCommitIcon)
    }
  }

  private fun createCommitChooserActionLink(
    scope: CoroutineScope,
    commitsVm: GHPRCommitsViewModel,
    diffBridge: GHPRDiffController
  ): JComponent {
    return ActionLink().apply {
      horizontalAlignment = SwingConstants.RIGHT
      setDropDownLinkIcon()
      bindText(scope, combine(commitsVm.selectedCommit, commitsVm.reviewCommits) { selectedCommit, commits ->
        if (selectedCommit != null) {
          val metrics = getFontMetrics(font)
          val commitHashWidth = calculateCommitHashWidth(metrics, commits)
          preferredSize = JBDimension(commitHashWidth, preferredSize.height, true)
          return@combine selectedCommit.abbreviatedOid
        }
        else {
          preferredSize = null
          return@combine CollaborationToolsBundle.message("review.details.commits.popup.text", commits.size)
        }
      })
      bindDisabled(scope, commitsVm.reviewCommits.map { commits ->
        commits.size <= 1
      })
      addActionListener(createCommitPopupAction(scope, commitsVm, diffBridge))
    }
  }

  private fun createCommitPopupAction(
    scope: CoroutineScope,
    commitsVm: GHPRCommitsViewModel,
    diffBridge: GHPRDiffController
  ): ActionListener = ActionListener { event ->
    val popupState: PopupState<JBPopup> = PopupState.forPopup()
    val parentComponent = event.source as? JComponent ?: return@ActionListener
    val point = RelativePoint.getSouthWestOf(parentComponent)
    scope.launch {
      val commits = commitsVm.reviewCommits.value
      val selectedCommit = commitsVm.selectedCommit.stateIn(this).value
      val popupItems: List<GHCommit?> = mutableListOf<GHCommit?>(null).apply {
        addAll(commits)
      }
      val chosenCommit = ChooserPopupUtil.showChooserPopup(
        point,
        popupState,
        popupItems,
        filteringMapper = { commit: GHCommit? ->
          commit?.messageHeadline ?: CollaborationToolsBundle.message("review.details.commits.popup.all", commits.size)
        },
        renderer = CommitRenderer { commit: GHCommit? ->
          createCommitPresenter(commit, selectedCommit, commitsVm.reviewCommits.value.size, commitsVm.ghostUser)
        }
      )

      if (chosenCommit == null) {
        diffBridge.activeTree = GHPRDiffController.ActiveTree.FILES
        commitsVm.selectAllCommits()
      }
      else {
        diffBridge.activeTree = GHPRDiffController.ActiveTree.COMMITS
        commitsVm.selectCommit(chosenCommit)
      }
    }
  }

  private fun calculateCommitHashWidth(metrics: FontMetrics, commits: List<GHCommit>): Int {
    require(commits.isNotEmpty())
    val longestCommitHash = commits.maxOf { commit -> metrics.stringWidth(commit.abbreviatedOid) }
    return longestCommitHash + AllIcons.General.LinkDropTriangle.iconWidth + COMMIT_HASH_OFFSET
  }

  private fun createCommitPresenter(
    commit: GHCommit?,
    selectedCommit: GHCommit?,
    commitsCount: Int,
    ghostUser: GHUser
  ): SelectableWrapper<CommitPresenter> {
    val isSelected = commit == selectedCommit
    val commitPresenter = if (commit == null) {
      CommitPresenter.AllCommits(title = CollaborationToolsBundle.message("review.details.commits.popup.all", commitsCount))
    }
    else {
      val author = commit.author?.user ?: ghostUser
      CommitPresenter.SingleCommit(
        title = commit.messageHeadlineHTML,
        author = author.getPresentableName(),
        committedDate = commit.committedDate
      )
    }

    return SelectableWrapper(commitPresenter, isSelected)
  }
}