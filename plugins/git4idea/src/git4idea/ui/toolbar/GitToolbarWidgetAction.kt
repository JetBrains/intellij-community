// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.toolbar

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.Tooltip
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.impl.ToolbarComboWidget
import com.intellij.ui.popup.util.PopupImplUtil
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopup
import git4idea.ui.branch.GitBranchesTreePopup
import icons.DvcsImplIcons
import java.awt.event.InputEvent
import javax.swing.Icon
import javax.swing.JComponent

private val projectKey = Key.create<Project>("git-widget-project")
private val repositoryKey = Key.create<GitRepository>("git-widget-repository")
private val changesKey = Key.create<MyRepoChanges>("git-widget-changes")

internal class GitToolbarWidgetAction : AnAction(), CustomComponentAction {

  override fun actionPerformed(e: AnActionEvent) {}

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent = GitToolbarWidget(presentation)

  override fun update(e: AnActionEvent) {
    val project = e.project
    val repository = project?.let { GitBranchUtil.guessWidgetRepository(project, e.dataContext) }

    e.presentation.putClientProperty(projectKey, project)
    e.presentation.putClientProperty(repositoryKey, repository)
    e.presentation.text = repository?.calcText() ?: GitBundle.message("git.toolbar.widget.no.repo")
    e.presentation.icon = repository.calcIcon()
    e.presentation.description = repository?.calcTooltip() ?: GitBundle.message("git.toolbar.widget.no.repo.tooltip")
    e.presentation.isEnabled = e.isFromActionToolbar

    val changes = repository?.currentBranchName?.let { branch ->
      val incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(project)
      MyRepoChanges(incomingOutgoingManager.hasIncomingFor(repository, branch), incomingOutgoingManager.hasOutgoingFor(repository, branch))
    } ?: MyRepoChanges(false, false)
    e.presentation.putClientProperty(changesKey, changes)
  }

  @NlsSafe
  private fun GitRepository.calcText(): String {
    return StringUtil.escapeMnemonics(GitBranchUtil.getDisplayableBranchText(this, ::cutText))
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

  private val MAX_TEXT_LENGTH = 24
  private val SHORTENED_BEGIN_PART = 16
  private val SHORTENED_END_PART = 8


  @NlsSafe
  private fun cutText(value: @NlsSafe String): String {
    if (value.length <= MAX_TEXT_LENGTH) return value

    val beginRange = IntRange(0, SHORTENED_BEGIN_PART - 1)
    val endRange = IntRange(value.length - SHORTENED_END_PART, value.length - 1)
    return value.substring(beginRange) + "..." + value.substring(endRange)
  }
}

private val INCOMING_CHANGES_ICON = DvcsImplIcons.Incoming
private val OUTGOING_CHANGES_ICON = DvcsImplIcons.Outgoing

private class GitToolbarWidget(val presentation: Presentation) : ToolbarComboWidget() {

  val project: Project?
    get() = presentation.getClientProperty(projectKey)
  val repository: GitRepository?
    get() = presentation.getClientProperty(repositoryKey)

  init {
    presentation.addPropertyChangeListener { updateWidget() }
  }

  override fun updateWidget() {
    text = presentation.text
    toolTipText = presentation.description
    leftIcons = listOfNotNull(presentation.icon)
    rightIcons = presentation.getClientProperty(changesKey)?.let { changes ->
      val res = mutableListOf<Icon>()
      if (changes.incoming) res.add(INCOMING_CHANGES_ICON)
      if (changes.outgoing) res.add(OUTGOING_CHANGES_ICON)
      res
    } ?: emptyList()
  }

  override fun doExpand(e: InputEvent?) {
    project?.let { proj ->
      val repo = repository

      val popup: JBPopup
      val component = IdeFocusManager.getGlobalInstance().focusOwner ?: this
      val dataContext = DataManager.getInstance().getDataContext(component)
      if (repo != null) {
        popup =
          if (GitBranchesTreePopup.isEnabled()) GitBranchesTreePopup.create(proj)
          else GitBranchPopup.getInstance(proj, repo, dataContext).asListPopup()
      }
      else {
        val group = ActionManager.getInstance().getAction("Vcs.ToolbarWidget.CreateRepository") as ActionGroup
        val place = ActionPlaces.getPopupPlace(ActionPlaces.VCS_TOOLBAR_WIDGET)
        popup = JBPopupFactory.getInstance()
          .createActionGroupPopup(null, group, dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true, place)
      }
      PopupImplUtil.setPopupToggleButton(popup, this)
      popup.setRequestFocus(false)
      popup.showUnderneathOf(this)
    }
  }
}

private data class MyRepoChanges(val incoming: Boolean, val outgoing: Boolean)
