// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.Icon
import javax.swing.JComponent

class GitLabMergeRequestEditorReviewModeActionProvider : InspectionWidgetActionProvider {
  override fun createAction(editor: Editor): AnAction? {
    val project: Project? = editor.project
    val potentialEditor = GitLabMergeRequestEditorReviewController.isPotentialEditor(editor)
    return if (project == null || project.isDefault || !potentialEditor) null else ReviewModeActionGroup(editor)
  }
}

private class ReviewModeActionGroup(private val editor: Editor) : ActionGroup(), DumbAware {
  private val reviewModeAction = ReviewModeAction()

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val vm = editor.getUserData(GitLabMergeRequestEditorReviewViewModel.KEY)
    e.presentation.isEnabledAndVisible = vm != null
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> = arrayOf(reviewModeAction, Separator.create())

  private inner class ReviewModeAction : DumbAwareAction(), CustomComponentAction {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
      object : ActionButtonWithText(this, presentation, place, JBUI.size(18)) {
        // button is not revalidated on hover, so the size of hovered/non-hovered must be constant
        override fun iconTextSpace(): Int = JBUI.scale(2)
      }.also {
        it.foreground = JBColor.lazy { editor.colorsScheme.getColor(FOREGROUND) ?: FOREGROUND.defaultColor }
        it.font = JBFont.small()
      }

    override fun actionPerformed(e: AnActionEvent) {
      val vm = editor.getUserData(GitLabMergeRequestEditorReviewViewModel.KEY) ?: return
      vm.toggleReviewMode()
    }

    override fun update(e: AnActionEvent) {
      val vm = editor.getUserData(GitLabMergeRequestEditorReviewViewModel.KEY)
      val selected = vm?.isReviewModeEnabled?.value ?: false
      val synced = !(vm?.localRepositorySyncStatus?.value?.incoming ?: false)
      val presentation = e.presentation
      with(presentation) {
        if (selected) {
          text = GitLabBundle.message("merge.request.review.mode.title")
          icon = if (synced) EmptyIcon.ICON_16 else getWarningIcon()
          hoveredIcon = AllIcons.Actions.CloseDarkGrey
          description = GitLabBundle.message("merge.request.review.mode.exit.description")
          val tooltip = HelpTooltip()
            .setTitle(GitLabBundle.message("merge.request.review.mode.exit.description"))
            .setDescription(GitLabBundle.message("merge.request.review.mode.description"))
          putClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP, tooltip)
        }
        else {
          text = null
          icon = AllIcons.Actions.Preview
          hoveredIcon = null
          description = GitLabBundle.message("merge.request.review.mode.enter.description")
          val tooltip = HelpTooltip()
            .setTitle(GitLabBundle.message("merge.request.review.mode.enter.description"))
            //TODO: better description
            .setDescription(GitLabBundle.message("merge.request.review.mode.description"))
          putClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP, tooltip)
        }
      }
    }

    private fun getWarningIcon(): Icon = HighlightDisplayLevel.find(HighlightSeverity.WARNING)?.icon ?: AllIcons.General.Warning
  }
}

private val FOREGROUND = ColorKey.createColorKey("ActionButton.iconTextForeground", UIUtil.getContextHelpForeground())

