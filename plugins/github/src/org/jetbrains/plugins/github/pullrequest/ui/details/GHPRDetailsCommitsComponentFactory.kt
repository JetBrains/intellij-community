// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.details.CommitRenderer
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
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRCommitsViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRDiffController
import java.awt.FontMetrics
import java.awt.event.ActionListener
import java.util.*
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
        commits.size == 1
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
      val popupItems: List<GHCommit?> = mutableListOf<GHCommit?>(null).apply {
        addAll(commits)
      }
      val selectedCommit = ChooserPopupUtil.showChooserPopup(
        point,
        popupState,
        popupItems,
        filteringMapper = { commit: GHCommit? ->
          commit?.messageHeadline ?: CollaborationToolsBundle.message("review.details.commits.popup.all", commits.size)
        },
        renderer = object : CommitRenderer<GHCommit?>() {
          override fun isCommitSelected(value: GHCommit?): Boolean {
            return value == commitsVm.selectedCommit.value
          }

          override fun getAllCommitsText(): String {
            return CollaborationToolsBundle.message("review.details.commits.popup.all", commitsVm.reviewCommits.value.size)
          }

          override fun getCommitTitle(value: GHCommit?): String {
            return value!!.messageHeadline
          }

          override fun getAuthor(value: GHCommit?): String {
            val author = value!!.author?.user ?: commitsVm.ghostUser
            return author.getPresentableName()
          }

          override fun getDate(value: GHCommit?): Date {
            return value!!.committedDate
          }
        }
      )

      commitsVm.selectCommit(selectedCommit)
      diffBridge.activeTree = GHPRDiffController.ActiveTree.COMMITS
    }
  }

  private fun calculateCommitHashWidth(metrics: FontMetrics, commits: List<GHCommit>): Int {
    val longestCommitHash = commits.maxOf { commit -> metrics.stringWidth(commit.abbreviatedOid) } // list always is not empty
    return longestCommitHash + AllIcons.General.LinkDropTriangle.iconWidth + COMMIT_HASH_OFFSET
  }
}