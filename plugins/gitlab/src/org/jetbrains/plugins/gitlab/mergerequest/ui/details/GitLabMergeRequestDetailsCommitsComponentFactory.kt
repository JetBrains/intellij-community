// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil
import com.intellij.collaboration.ui.util.bindDisabled
import com.intellij.collaboration.ui.util.bindText
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.ui.popup.PopupState
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.*
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestChangesViewModel
import java.awt.Component
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

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
        commits.size == 1
      })

      addActionListener { event ->
        val popupState: PopupState<JBPopup> = PopupState.forPopup()
        val parentComponent = event.source as? JComponent ?: return@addActionListener
        val point = RelativePoint.getSouthWestOf(parentComponent)
        scope.launch {
          val commits = commitsVm.reviewCommits.value
          val popupItems: List<GitLabCommitDTO?> = mutableListOf<GitLabCommitDTO?>(null).apply {
            addAll(commits)
          }

          val selectedCommit = ChooserPopupUtil.showChooserPopup(
            point,
            popupState,
            popupItems,
            filteringMapper = { commit: GitLabCommitDTO? ->
              commit?.title ?: CollaborationToolsBundle.message("review.details.commits.popup.all", commits.size)
            },
            renderer = CommitRenderer(commitsVm)
          )

          commitsVm.selectCommit(selectedCommit)
        }
      }
    }
  }

  private class CommitRenderer(private val commitsVm: GitLabMergeRequestChangesViewModel) : ListCellRenderer<GitLabCommitDTO?> {
    private val iconLabel = JLabel().apply {
      border = JBUI.Borders.empty(0, ICON_LEFT_RIGHT_OFFSET)
    }
    private val allCommitsMessage = SimpleColoredComponent().apply {
      border = JBUI.Borders.empty(TOP_BOTTOM_OFFSET, 0)
    }
    private val commitMessage = SimpleColoredComponent().apply {
      border = JBUI.Borders.emptyTop(TOP_BOTTOM_OFFSET)
    }
    private val authorAndDate = SimpleColoredComponent().apply {
      border = JBUI.Borders.emptyBottom(TOP_BOTTOM_OFFSET)
    }
    private val textPanel = BorderLayoutPanel()
    private val commitPanel = BorderLayoutPanel()

    override fun getListCellRendererComponent(list: JList<out GitLabCommitDTO>,
                                              value: GitLabCommitDTO?,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      textPanel.removeAll()
      commitPanel.removeAll()

      allCommitsMessage.clear()
      commitMessage.clear()
      authorAndDate.clear()

      commitMessage.foreground = ListUiUtil.WithTallRow.foreground(isSelected, list.hasFocus())
      authorAndDate.foreground = ListUiUtil.WithTallRow.secondaryForeground(isSelected, list.hasFocus())

      iconLabel.icon = if (value == commitsVm.selectedCommit.value)
        AllIcons.Actions.Checked_selected
      else
        JBUIScale.scaleIcon(EmptyIcon.create(EMPTY_ICON_SIZE))

      if (value == null) {
        allCommitsMessage.append(CollaborationToolsBundle.message("review.details.commits.popup.all", commitsVm.reviewCommits.value.size))
        textPanel.addToCenter(allCommitsMessage)
      }
      else {
        val author = value.author
        commitMessage.append(value.title.orEmpty())
        authorAndDate.append("${author.name} ${DateFormatUtil.formatPrettyDateTime(value.authoredDate)}")
        textPanel.addToCenter(commitMessage).addToBottom(authorAndDate)
      }

      return commitPanel.addToLeft(iconLabel).addToCenter(textPanel).apply {
        border = if (value == null) IdeBorderFactory.createBorder(SideBorder.BOTTOM) else null
        UIUtil.setBackgroundRecursively(this, ListUiUtil.WithTallRow.background(list, isSelected, list.hasFocus()))
      }
    }

    companion object {
      private const val TOP_BOTTOM_OFFSET = 4
      private const val ICON_LEFT_RIGHT_OFFSET = 8
      private const val EMPTY_ICON_SIZE = 12
    }
  }
}