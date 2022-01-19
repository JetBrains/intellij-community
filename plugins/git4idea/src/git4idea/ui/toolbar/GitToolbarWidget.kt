// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.toolbar

import com.intellij.dvcs.DvcsUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.ToolbarComboWidget
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbarProjectWidgetFactory
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbarWidgetFactory.Position
import git4idea.GitUtil
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchIncomingOutgoingManager.GitIncomingOutgoingListener
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.ui.branch.GitBranchPopup
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

private class GitWidgetUpdater(val project: Project, val widget: GitToolbarWidget) : GitRepositoryChangeListener, GitIncomingOutgoingListener, ProjectManagerListener {
  private val swingExecutor: Executor = Executor { run -> SwingUtilities.invokeLater(run) }

  private var repository: GitRepository? = null

  private val INCOMING_CHANGES_ICON = AllIcons.Actions.CheckOut
  private val OUTGOING_CHANGES_ICON = AllIcons.Vcs.Push

  init {
    repository = guessCurrentRepo(project)
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
    widget.text = repository?.currentBranchName?.let { cutText(it) } ?: GitBundle.message("git.toolbar.widget.no.repo")
    widget.toolTipText = repository?.currentBranchName?.let { GitBundle.message("git.toolbar.widget.tooltip", it) }
    updateIcons()
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

  private fun guessCurrentRepo(project: Project): GitRepository? {
    val settings = GitVcsSettings.getInstance(project)
    return DvcsUtil.guessCurrentRepositoryQuick(project, GitUtil.getRepositoryManager(project), settings.recentRootPath)
  }
}

private const val MAX_TEXT_LENGTH = 24
private const val SHORTENED_BEGIN_PART = 16
private const val SHORTENED_END_PART = 8

private fun cutText(value: String?): String? {
  if (value == null || value.length <= MAX_TEXT_LENGTH) return value

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
    val proj = project
    val repo = repository
    if (proj != null && repo != null) {
      val dataManager = DataManager.getInstance()
      val listPopup = GitBranchPopup.getInstance(proj, repo, dataManager.getDataContext(this)).asListPopup()
      listPopup.showUnderneathOf(this)
    }
  }

  override fun dispose() {}

  override fun removeNotify() {
    super.removeNotify()
    Disposer.dispose(this)
  }
}