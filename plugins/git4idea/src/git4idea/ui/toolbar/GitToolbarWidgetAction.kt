// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.toolbar

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.ToolbarComboWidgetUI
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.Tooltip
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.impl.ExpandableComboAction
import com.intellij.openapi.wm.impl.ToolbarComboWidget
import com.intellij.ui.popup.util.PopupImplUtil
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopup
import git4idea.ui.branch.GitBranchPopupActions
import git4idea.ui.branch.popup.GitBranchesTreePopup
import icons.DvcsImplIcons
import javax.swing.Icon
import javax.swing.JComponent

private val projectKey = Key.create<Project>("git-widget-project")
private val repositoryKey = Key.create<GitRepository>("git-widget-repository")
private val changesKey = Key.create<MyRepoChanges>("git-widget-changes")

internal class GitToolbarWidgetAction : ExpandableComboAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun createPopup(event: AnActionEvent): JBPopup? {
    val project = event.project ?: return null
    val repository = GitBranchUtil.guessWidgetRepository(project, event.dataContext)

    val popup: JBPopup = if (repository != null) {
      if (GitBranchesTreePopup.isEnabled()) GitBranchesTreePopup.create(project)
      else GitBranchPopup.getInstance(project, repository, event.dataContext).asListPopup()
    }
    else {
      val group = ActionManager.getInstance().getAction("Vcs.ToolbarWidget.CreateRepository") as ActionGroup
      val place = ActionPlaces.getPopupPlace(ActionPlaces.VCS_TOOLBAR_WIDGET)
      JBPopupFactory.getInstance()
        .createActionGroupPopup(null, group, event.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true, place)
    }
    val widget = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? ToolbarComboWidget
    PopupImplUtil.setPopupToggleButton(popup, widget)

    return popup
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val comp = super.createCustomComponent(presentation, place)
    (comp.ui as? ToolbarComboWidgetUI)?.setMaxWidth(Int.MAX_VALUE)
    return comp
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    val widget = component as? ToolbarComboWidget ?: return
    widget.text = presentation.text
    widget.toolTipText = presentation.description
    widget.leftIcons = listOfNotNull(presentation.icon)
    widget.rightIcons = presentation.getClientProperty(changesKey)?.let { changes ->
      val res = mutableListOf<Icon>()
      if (changes.incoming) res.add(DvcsImplIcons.Incoming)
      if (changes.outgoing) res.add(DvcsImplIcons.Outgoing)
      res
    } ?: emptyList()
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val repository = project?.let { GitBranchUtil.guessWidgetRepository(project, e.dataContext) }

    e.presentation.putClientProperty(projectKey, project)
    e.presentation.putClientProperty(repositoryKey, repository)
    e.presentation.text = calcText(project, repository)
    e.presentation.icon = repository?.calcIcon()
    e.presentation.description = repository?.calcTooltip() ?: GitBundle.message("git.toolbar.widget.no.repo.tooltip")

    val changes = repository?.currentBranchName?.let { branch ->
      val incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(project)
      MyRepoChanges(incomingOutgoingManager.hasIncomingFor(repository, branch), incomingOutgoingManager.hasOutgoingFor(repository, branch))
    } ?: MyRepoChanges(incoming = false, outgoing = false)
    e.presentation.putClientProperty(changesKey, changes)
  }

  @NlsSafe
  private fun calcText(project: Project?, repository: GitRepository?): String {
    project ?: return  GitBundle.message("git.toolbar.widget.no.repo")
    repository ?: return  GitBundle.message("git.toolbar.widget.no.repo")

    return StringUtil.escapeMnemonics(GitBranchUtil.getDisplayableBranchText(repository) { branchName ->
      GitBranchPopupActions.truncateBranchName(branchName, project)
    })
  }

  private fun GitRepository?.calcIcon(): Icon {
    this ?: return AllIcons.Vcs.Branch

    if (state != Repository.State.NORMAL) {
      return AllIcons.General.Warning
    }
    return AllIcons.Vcs.Branch
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
}

private data class MyRepoChanges(val incoming: Boolean, val outgoing: Boolean)
