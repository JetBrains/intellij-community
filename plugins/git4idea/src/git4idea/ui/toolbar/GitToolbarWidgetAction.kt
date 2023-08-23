// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.toolbar

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.icons.AllIcons
import com.intellij.icons.ExpUiIcons
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.customization.groupContainsAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.Tooltip
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.wm.impl.ExpandableComboAction
import com.intellij.openapi.wm.impl.ToolbarComboButton
import com.intellij.ui.util.maximumWidth
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchUtil
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions
import git4idea.ui.branch.GitBranchPopupActions.BRANCH_NAME_LENGTH_DELTA
import git4idea.ui.branch.GitBranchPopupActions.BRANCH_NAME_SUFFIX_LENGTH
import git4idea.ui.branch.GitCurrentBranchPresenter
import git4idea.ui.branch.popup.GitBranchesTreePopup
import icons.DvcsImplIcons
import javax.swing.Icon
import javax.swing.JComponent

private val repositoryKey = Key.create<GitRepository>("git-widget-repository")
private val changesKey = Key.create<MyRepoChanges>("git-widget-changes")

private const val GIT_WIDGET_BRANCH_NAME_MAX_LENGTH: Int = 80
private const val GIT_WIDGET_PLACEHOLDER_KEY = "git-widget-placeholder"

internal class GitToolbarWidgetAction : ExpandableComboAction() {
  private val widgetIcon = ExpUiIcons.General.Vcs

  private val actionsWithIncomingOutgoingEnabled = GitToolbarActions.isEnabledAndVisible()

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun createPopup(event: AnActionEvent): JBPopup? {
    val project = event.project ?: return null
    val repository = event.presentation.getClientProperty(repositoryKey)

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
    val shouldShowIncoming =
      actionsWithIncomingOutgoingEnabled && !groupContainsAction("MainToolbarNewUI", "main.toolbar.git.update.project", schema)
    val shouldShowOutgoing =
      actionsWithIncomingOutgoingEnabled && !groupContainsAction("MainToolbarNewUI", "main.toolbar.git.push", schema)

    widget.rightIcons = presentation.getClientProperty(changesKey)?.let { changes ->
      val res = mutableListOf<Icon>()
      if (changes.incoming && (!actionsWithIncomingOutgoingEnabled || shouldShowIncoming)) res.add(DvcsImplIcons.Incoming)
      if (changes.outgoing && (!actionsWithIncomingOutgoingEnabled || shouldShowOutgoing)) res.add(DvcsImplIcons.Outgoing)
      res
    } ?: emptyList()
  }

  override fun update(e: AnActionEvent) {
    val project = e.project

    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val gitRepository = GitBranchUtil.guessWidgetRepository(project, e.dataContext)
    val state = getWidgetState(project, gitRepository)

    if (gitRepository != null && gitRepository != e.presentation.getClientProperty(repositoryKey)) {
      GitVcsSettings.getInstance(project).setRecentRoot(gitRepository.root.path)
    }
    e.presentation.putClientProperty(repositoryKey, gitRepository)

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
          icon = if (placeholder != null) widgetIcon else null
          description = GitBundle.message("git.toolbar.widget.no.repo.tooltip")
        }
      }

      is GitWidgetState.Repo -> {
        val repo = state.repository
        with(e.presentation) {
          isEnabledAndVisible = true

          val customPresentation = GitCurrentBranchPresenter.getPresentation(repo)
          if (customPresentation == null) {
            text = calcText(project, repo)
            icon = repo.calcIcon()
            description = repo.calcTooltip()
          }
          else {
            text = customPresentation.text
            icon = customPresentation.icon
            description = customPresentation.description
          }
        }
      }
    }

    val changes = gitRepository?.currentBranchName?.let { branch ->
      val incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(project)
      MyRepoChanges(incomingOutgoingManager.hasIncomingFor(gitRepository, branch),
                    incomingOutgoingManager.hasOutgoingFor(gitRepository, branch))
    } ?: MyRepoChanges(incoming = false, outgoing = false)
    e.presentation.putClientProperty(changesKey, changes)
  }

  @NlsSafe
  private fun calcText(project: Project, repository: GitRepository): String {
    return StringUtil.escapeMnemonics(GitBranchUtil.getDisplayableBranchText(repository) { branchName ->
      GitBranchPopupActions.truncateBranchName(project, branchName,
                                               GIT_WIDGET_BRANCH_NAME_MAX_LENGTH,
                                               BRANCH_NAME_SUFFIX_LENGTH,
                                               BRANCH_NAME_LENGTH_DELTA)
    }).also { updatePlaceholder(project, it) }
  }

  private fun GitRepository.calcIcon(): Icon {
    if (state != Repository.State.NORMAL) {
      return AllIcons.General.Warning
    }
    return widgetIcon
  }

  @Tooltip
  private fun GitRepository.calcTooltip(): String {
    if (state == Repository.State.DETACHED) {
      return GitBundle.message("git.status.bar.widget.tooltip.detached")
    }

    var message = DvcsBundle.message("tooltip.branch.widget.vcs.branch.name.text", GitVcs.DISPLAY_NAME.get(),
                                     GitBranchUtil.getBranchNameOrRev(this))
    if (!GitUtil.justOneGitRepository(project)) {
      message += "\n"
      message += DvcsBundle.message("tooltip.branch.widget.root.name.text", root.name)
    }
    return message
  }

  companion object {
    fun updatePlaceholder(project: Project, newPlaceholder: @NlsSafe String?) {
      PropertiesComponent.getInstance(project).setValue(GIT_WIDGET_PLACEHOLDER_KEY, newPlaceholder)
    }

    fun getPlaceholder(project: Project): @NlsSafe String? = PropertiesComponent.getInstance(project).getValue(GIT_WIDGET_PLACEHOLDER_KEY)

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

private data class MyRepoChanges(val incoming: Boolean, val outgoing: Boolean)
