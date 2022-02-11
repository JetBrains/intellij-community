// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import git4idea.branch.GitBranchUtil
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.comment.GHSuggestedChangeApplier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.*

class GHPRReviewSuggestedChangeComponentFactory(
  private val project: Project,
  private val threadId: String,
  private val isOutdated: Boolean,
  private val suggestionApplier: GHSuggestedChangeApplier,
  private val reviewDataProvider: GHPRReviewDataProvider,
  private val detailsDataProvider: GHPRDetailsDataProvider
) {
  private val scheme = EditorColorsManager.getInstance().globalScheme
  private val borderColor = scheme.getColor(EditorColors.TEARLINE_COLOR) ?: JBColor.border()

  fun create(content: String): JComponent = JPanel(BorderLayout()).apply {
    isOpaque = false
    border = JBUI.Borders.customLine(borderColor, BORDER_SIZE)

    val titleLabel = JLabel(GithubBundle.message("pull.request.timeline.comment.suggested.changes")).apply {
      isOpaque = false
      foreground = UIUtil.getContextHelpForeground()
    }

    val applyLocallyAction = ApplyLocallySuggestedChangeAction()
    val applyButton = JButton(applyLocallyAction).apply {
      isOpaque = false
    }

    val topPanel = JPanel(BorderLayout()).apply {
      isOpaque = false
      border = JBUI.Borders.compound(JBUI.Borders.customLine(borderColor, 0, 0, BORDER_SIZE, 0),
                                     JBUI.Borders.empty(EMPTY_GAP, EMPTY_GAP))

      add(titleLabel, BorderLayout.WEST)
      add(applyButton, BorderLayout.EAST)
    }

    add(topPanel, BorderLayout.NORTH)
    add(HtmlEditorPane(content), BorderLayout.CENTER)

    if (isCurrentBranchDifferentWithPullRequest()) {
      applyButton.isEnabled = false
      applyButton.toolTipText = GithubBundle.message("pull.request.timeline.comment.suggested.changes.tooltip.different.branch")
    }
    else if (isOutdated) {
      applyButton.isEnabled = false
      applyButton.toolTipText = GithubBundle.message("pull.request.timeline.comment.suggested.changes.tooltip.outdated")
    }
  }

  private fun isCurrentBranchDifferentWithPullRequest(): Boolean {
    val currentRepository = GitBranchUtil.getCurrentRepository(project)
    val currentBranchName = currentRepository?.let { GitBranchUtil.getBranchNameOrRev(it) } ?: return true

    val pullRequestSourceBranchName = detailsDataProvider.loadedDetails?.headRefName

    return currentBranchName != pullRequestSourceBranchName
  }

  private inner class ApplyLocallySuggestedChangeAction : AbstractAction(
    GithubBundle.message("pull.request.timeline.comment.suggested.changes.button")
  ) {
    override fun actionPerformed(event: ActionEvent) {
      if (isCurrentBranchDifferentWithPullRequest()) {
        Messages.showWarningDialog(GithubBundle.message("pull.request.timeline.comment.suggested.changes.error.branches.message"),
                                   GithubBundle.message("pull.request.timeline.comment.suggested.changes.error.branches.title"))
        return
      }

      val task = object : Task.Backgroundable(
        project,
        GithubBundle.message("pull.request.timeline.comment.suggested.changes.progress.bar.apply"),
        true
      ) {
        override fun run(indicator: ProgressIndicator) {
          val applyStatus = suggestionApplier.applySuggestedChange()
          if (applyStatus == ApplyPatchStatus.SUCCESS) {
            reviewDataProvider.resolveThread(EmptyProgressIndicator(), threadId)
          }
        }
      }
      task.queue()
    }
  }

  companion object {
    private val BORDER_SIZE = 1
    private val EMPTY_GAP = 4
  }
}