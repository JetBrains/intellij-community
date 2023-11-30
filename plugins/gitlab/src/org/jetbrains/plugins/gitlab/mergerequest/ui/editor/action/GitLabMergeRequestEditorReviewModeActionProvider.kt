// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor.action

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestEditorReviewController
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestEditorReviewViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.Icon

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

  private inner class ReviewModeAction : ActionGroup(), DumbAware {
    private val updateAction = UpdateAction()

    private val disableReviewAction =
      ViewOptionToggleAction(DiscussionsViewOption.DONT_SHOW,
                             GitLabBundle.message("action.GitLab.Merge.Request.Review.Editor.Disable.text"))
    private val hideResolvedAction =
      ViewOptionToggleAction(DiscussionsViewOption.UNRESOLVED_ONLY,
                             GitLabBundle.message("action.GitLab.Merge.Request.Review.Editor.Show.Unresolved.text"))
    private val showAllAction =
      ViewOptionToggleAction(DiscussionsViewOption.ALL,
                             GitLabBundle.message("action.GitLab.Merge.Request.Review.Editor.Show.All.text"))

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
      arrayOf(updateAction, Separator.create(), disableReviewAction, hideResolvedAction, showAllAction)

    override fun displayTextInToolbar(): Boolean = true

    override fun useSmallerFontForTextInToolbar(): Boolean = true

    init {
      with(templatePresentation) {
        isPopupGroup = true
        putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)
        description = GitLabBundle.message("merge.request.review.mode.description.title")
        val tooltip = HelpTooltip()
          .setTitle(GitLabBundle.message("merge.request.review.mode.description.title"))
          .setDescription(GitLabBundle.message("merge.request.review.mode.description"))
        putClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP, tooltip)
      }
    }

    override fun update(e: AnActionEvent) {
      val vm = editor.getUserData(GitLabMergeRequestEditorReviewViewModel.KEY)
      if (vm == null) {
        e.presentation.isEnabledAndVisible = false
        return
      }
      val shown = vm.discussionsViewOption.value != DiscussionsViewOption.DONT_SHOW
      val synced = vm.localRepositorySyncStatus.value?.incoming?.not() ?: true
      with(e.presentation) {
        if (shown) {
          text = GitLabBundle.message("merge.request.review.mode.title")
          icon = if (synced) null else getWarningIcon()
        }
        else {
          text = null
          icon = AllIcons.Actions.Preview
        }
      }
    }

    private fun getWarningIcon(): Icon = HighlightDisplayLevel.find(HighlightSeverity.WARNING)?.icon ?: AllIcons.General.Warning

    private inner class UpdateAction
      : DumbAwareAction(GitLabBundle.message("action.GitLab.Merge.Request.Review.Editor.Update.text"), null, AllIcons.Actions.CheckOut) {

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

      override fun update(e: AnActionEvent) {
        val vm = editor.getUserData(GitLabMergeRequestEditorReviewViewModel.KEY)
        if (vm == null) {
          e.presentation.isEnabledAndVisible = false
          return
        }
        e.presentation.isEnabledAndVisible = vm.localRepositorySyncStatus.value?.incoming == true
      }

      override fun actionPerformed(e: AnActionEvent) {
        val vm = editor.getUserData(GitLabMergeRequestEditorReviewViewModel.KEY) ?: return
        vm.updateBranch()
      }
    }

    private inner class ViewOptionToggleAction(private val option: DiscussionsViewOption,
                                               text: @NlsActions.ActionText String) : ToggleAction(text) {

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

      override fun update(e: AnActionEvent) {
        super.update(e)
        val vm = editor.getUserData(GitLabMergeRequestEditorReviewViewModel.KEY)
        e.presentation.isEnabledAndVisible = vm != null
      }

      override fun isSelected(e: AnActionEvent): Boolean {
        val vm = editor.getUserData(GitLabMergeRequestEditorReviewViewModel.KEY) ?: return false
        return vm.discussionsViewOption.value == option
      }

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        val vm = editor.getUserData(GitLabMergeRequestEditorReviewViewModel.KEY) ?: return
        vm.setDiscussionsViewOption(option)
      }
    }
  }
}
