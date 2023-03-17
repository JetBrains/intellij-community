// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

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
import com.intellij.util.ui.JBFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestChangesViewModel
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JLabel

internal object GitLabMergeRequestDetailsCommitsComponentFactory {
  private const val COMPONENTS_GAP = 4

  fun create(scope: CoroutineScope, commitsVm: GitLabMergeRequestChangesViewModel): JComponent {
    val commitsPopupTitle = JLabel().apply {
      font = JBFont.regular().asBold()
      bindText(scope, commitsVm.reviewCommits.map { commits ->
        CollaborationToolsBundle.message("review.details.commits.title.text", commits.size)
      })
    }
    val commitsPopup = createCommitChooserActionLink(scope, commitsVm)
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

  private fun createCommitChooserActionLink(scope: CoroutineScope, commitsVm: GitLabMergeRequestChangesViewModel): JComponent {
    return ActionLink().apply {
      setDropDownLinkIcon()
      bindText(scope, combine(commitsVm.selectedCommit, commitsVm.reviewCommits) { selectedCommit, commits ->
        selectedCommit?.shortId ?: CollaborationToolsBundle.message("review.details.commits.popup.text", commits.size)
      })
      bindDisabled(scope, commitsVm.reviewCommits.map { commits ->
        commits.size <= 1
      })

      addActionListener { event ->
        val popupState: PopupState<JBPopup> = PopupState.forPopup()
        val parentComponent = event.source as? JComponent ?: return@addActionListener
        val point = RelativePoint.getSouthWestOf(parentComponent)
        scope.launch {
          val commits = commitsVm.reviewCommits.value
          val selectedCommit = commitsVm.selectedCommit.stateIn(this).value
          val popupItems: List<GitLabCommitDTO?> = mutableListOf<GitLabCommitDTO?>(null).apply {
            addAll(commits)
          }

          val chosenCommit = ChooserPopupUtil.showChooserPopup(
            point,
            popupState,
            popupItems,
            filteringMapper = { commit: GitLabCommitDTO? ->
              commit?.title ?: CollaborationToolsBundle.message("review.details.commits.popup.all", commits.size)
            },
            renderer = CommitRenderer { commit: GitLabCommitDTO? ->
              createCommitPresenter(commit, selectedCommit, commits.size)
            }
          )

          commitsVm.selectCommit(chosenCommit)
        }
      }
    }
  }

  private fun createCommitPresenter(
    commit: GitLabCommitDTO?,
    selectedCommit: GitLabCommitDTO?,
    commitsCount: Int
  ): SelectableWrapper<CommitPresenter> {
    val isSelected = commit == selectedCommit
    val commitPresenter = if (commit == null) {
      CommitPresenter.AllCommits(title = CollaborationToolsBundle.message("review.details.commits.popup.all", commitsCount))
    }
    else {
      CommitPresenter.SingleCommit(
        title = commit.title.orEmpty(),
        author = commit.author.name,
        committedDate = commit.authoredDate
      )
    }

    return SelectableWrapper(commitPresenter, isSelected)
  }
}