// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.toolbar

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.ToolbarComboWidget
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbarProjectWidgetFactory
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbarWidgetFactory.Position
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchIncomingOutgoingManager.GitIncomingOutgoingListener
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.ui.branch.GitBranchPopup
import icons.DvcsImplIcons
import java.awt.event.InputEvent
import java.util.concurrent.Executor
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities

internal data class Changes(val incoming: Boolean, val outgoing: Boolean)

internal class GitToolbarWidgetFactory : MainToolbarProjectWidgetFactory, Disposable {
  override fun createWidget(project: Project): JComponent {
    val widget = GitToolbarWidget()
    GitWidgetUpdater(project, widget).subscribe()
    return widget
  }

  override fun dispose() {}

  override fun getPosition(): Position = Position.Left
}

private class GitWidgetUpdater(val project: Project, val widget: GitToolbarWidget)
  : GitRepositoryChangeListener, GitIncomingOutgoingListener, ProjectManagerListener {
  private val swingExecutor: Executor = Executor { run -> SwingUtilities.invokeLater(run) }

  private var repository: GitRepository? = null

  private val INCOMING_CHANGES_ICON = DvcsImplIcons.Incoming
  private val OUTGOING_CHANGES_ICON = DvcsImplIcons.Outgoing

  init {
    repository = GitBranchUtil.guessWidgetRepository(project)
    updateWidget()
  }

  fun subscribe() {
    val connection = project.messageBus.connect(widget)
    connection.subscribe(GitRepository.GIT_REPO_CHANGE, this)
    connection.subscribe(GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED, this)
  }

  override fun incomingOutgoingInfoChanged() {
    swingExecutor.execute { updateIcons() }
  }

  override fun repositoryChanged(repo: GitRepository) {
    swingExecutor.execute {
      repository = repo
      updateWidget()
    }
  }

  private fun updateWidget() {
    widget.project = project
    widget.repository = repository
    widget.text = repository?.calcText() ?: GitBundle.message("git.toolbar.widget.no.repo")
    widget.toolTipText = repository?.calcTooltip() ?: GitBundle.message("git.toolbar.widget.no.repo.tooltip")
    updateIcons()
  }

  private fun GitRepository.calcText(): String = cutText(GitBranchUtil.getBranchNameOrRev(this))

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

  private fun updateIcons() {
    val icons = repository?.currentBranchName?.let { branch ->
      val res = mutableListOf<Icon>()
      val incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(project)
      if (incomingOutgoingManager.hasIncomingFor(repository, branch)) res.add(INCOMING_CHANGES_ICON)
      if (incomingOutgoingManager.hasOutgoingFor(repository, branch)) res.add(OUTGOING_CHANGES_ICON)
      res
    } ?: emptyList()

    widget.rightIcons = icons
  }
}

private const val MAX_TEXT_LENGTH = 24
private const val SHORTENED_BEGIN_PART = 16
private const val SHORTENED_END_PART = 8

private fun cutText(value: String): String {
  if (value.length <= MAX_TEXT_LENGTH) return value

  val beginRange = IntRange(0, SHORTENED_BEGIN_PART - 1)
  val endRange = IntRange(value.length - SHORTENED_END_PART, value.length - 1)
  return value.substring(beginRange) + "..." + value.substring(endRange)
}

private class GitToolbarWidget : ToolbarComboWidget(), Disposable {

  var project: Project? = null
  var repository: GitRepository? = null

  init {
    leftIcons = listOf(AllIcons.Vcs.Branch)
  }

  override fun doExpand(e: InputEvent) {
    project?.let { proj ->
      val repo = repository

      val listPopup: ListPopup
      val dataContext = DataManager.getInstance().getDataContext(this)
      if (repo != null) {
        listPopup = GitBranchPopup.getInstance(proj, repo, dataContext).asListPopup()
      }
      else {
        val group = ActionManager.getInstance().getAction("Vcs.ToolbarWidget.CreateRepository") as ActionGroup
        val place = ActionPlaces.getPopupPlace(ActionPlaces.VCS_TOOLBAR_WIDGET)
        listPopup = JBPopupFactory.getInstance()
          .createActionGroupPopup(null, group, dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true, place)
      }
      listPopup.setRequestFocus(false)
      listPopup.showUnderneathOf(this)
    }
  }

  override fun dispose() {}

  override fun removeNotify() {
    super.removeNotify()
    Disposer.dispose(this)
  }
}