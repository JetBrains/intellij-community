// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.toolbar

import com.intellij.icons.ExpUiIcons
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.customization.groupContainsAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.wm.impl.ExpandableComboAction
import com.intellij.openapi.wm.impl.ToolbarComboButton
import com.intellij.ui.util.maximumWidth
import git4idea.GitVcs
import git4idea.branch.GitBranchSyncStatus
import git4idea.branch.GitBranchUtil
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitCurrentBranchPresenter
import git4idea.ui.branch.popup.GitBranchesTreePopup
import icons.DvcsImplIcons
import javax.swing.Icon
import javax.swing.JComponent

private val REPOSITORY_KEY = Key.create<GitRepository>("git-widget-repository")
private val SYNC_STATUS_KEY = Key.create<GitBranchSyncStatus>("git-widget-branch-sync-status")

private val WIDGET_ICON: Icon = ExpUiIcons.General.Vcs

private const val GIT_WIDGET_PLACEHOLDER_KEY = "git-widget-placeholder"

internal class GitToolbarWidgetAction : ExpandableComboAction(), DumbAware {

  private val actionsWithIncomingOutgoingEnabled = GitToolbarActions.isEnabledAndVisible()

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun createPopup(event: AnActionEvent): JBPopup? {
    val project = event.project ?: return null
    val repository = event.presentation.getClientProperty(REPOSITORY_KEY)

    val popup: JBPopup = if (repository != null) {
      GitBranchesTreePopup.create(project, repository)
    }
    else {
      updatePlaceholder(project, null)
      val group = ActionManager.getInstance().getAction("Vcs.ToolbarWidget.CreateRepository") as ActionGroup
      val place = ActionPlaces.getPopupPlace(ActionPlaces.VCS_TOOLBAR_WIDGET)
      JBPopupFactory.getInstance()
        .createActionGroupPopup(null, group, event.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true, place)
    }
    return popup
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return super.createCustomComponent(presentation, place).apply { maximumWidth = Int.MAX_VALUE }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    val widget = component as? ToolbarComboButton ?: return
    widget.text = presentation.text
    widget.toolTipText = presentation.description
    widget.leftIcons = listOfNotNull(presentation.icon)
    val schema = CustomActionsSchema.getInstance()

    val rightIcons = mutableListOf<Icon>()

    val syncStatus = presentation.getClientProperty(SYNC_STATUS_KEY)

    val showIncoming = !actionsWithIncomingOutgoingEnabled
                       || !groupContainsAction("MainToolbarNewUI", "main.toolbar.git.update.project", schema)
    if (showIncoming && syncStatus?.incoming == true) {
      rightIcons.add(DvcsImplIcons.Incoming)
    }

    val showOutgoing = !actionsWithIncomingOutgoingEnabled
                       || !groupContainsAction("MainToolbarNewUI", "main.toolbar.git.push", schema)
    if (showOutgoing && syncStatus?.outgoing == true) {
      rightIcons.add(DvcsImplIcons.Outgoing)
    }

    widget.rightIcons = rightIcons
  }

  override fun update(e: AnActionEvent) {
    val project = e.project

    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val gitRepository = GitBranchUtil.guessWidgetRepository(project, e.dataContext)
    val state = getWidgetState(project, gitRepository)

    if (gitRepository != null && gitRepository != e.presentation.getClientProperty(REPOSITORY_KEY)) {
      GitVcsSettings.getInstance(project).setRecentRoot(gitRepository.root.path)
    }
    e.presentation.putClientProperty(REPOSITORY_KEY, gitRepository)

    when (state) {
      GitWidgetState.OtherVcs -> {
        e.presentation.isEnabledAndVisible = false
        return
      }

      GitWidgetState.NoVcs -> {
        val placeholder = getPlaceholder(project)
        with(e.presentation) {
          isEnabledAndVisible = true
          text = placeholder ?: GitBundle.message("git.toolbar.widget.no.repo")
          icon = if (placeholder != null) WIDGET_ICON else null
          description = GitBundle.message("git.toolbar.widget.no.repo.tooltip")
        }
      }

      is GitWidgetState.Repo -> {
        with(e.presentation) {
          isEnabledAndVisible = true

          val presentation = GitCurrentBranchPresenter.getPresentation(state.repository)
          icon = presentation.icon ?: WIDGET_ICON
          text = presentation.text.also { updatePlaceholder(project, it) }
          description = presentation.description
          putClientProperty(SYNC_STATUS_KEY, presentation.syncStatus)
        }
      }
    }
  }

  companion object {
    const val BRANCH_NAME_MAX_LENGTH: Int = 80

    private fun updatePlaceholder(project: Project, newPlaceholder: @NlsSafe String?) {
      PropertiesComponent.getInstance(project).setValue(GIT_WIDGET_PLACEHOLDER_KEY, newPlaceholder)
    }

    private fun getPlaceholder(project: Project): @NlsSafe String? =
      PropertiesComponent.getInstance(project).getValue(GIT_WIDGET_PLACEHOLDER_KEY)

    fun getWidgetState(project: Project, gitRepository: GitRepository?): GitWidgetState {
      if (gitRepository != null) {
        return GitWidgetState.Repo(gitRepository)
      }

      val allVcss = ProjectLevelVcsManager.getInstance(project).allActiveVcss

      return when {
        allVcss.isEmpty() -> GitWidgetState.NoVcs
        allVcss.any { it.keyInstanceMethod != GitVcs.getKey() } -> GitWidgetState.OtherVcs
        else -> GitWidgetState.NoVcs
      }
    }
  }

  internal sealed class GitWidgetState {
    object NoVcs : GitWidgetState()

    class Repo(val repository: GitRepository) : GitWidgetState()

    object OtherVcs : GitWidgetState()
  }
}
