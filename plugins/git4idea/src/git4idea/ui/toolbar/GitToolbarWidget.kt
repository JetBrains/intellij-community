// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.toolbar

import com.intellij.dvcs.DvcsUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.ToolbarComboWidget
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbarWidgetFactory
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbarWidgetFactory.Position
import com.intellij.util.messages.MessageBusConnection
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

class GitToolbarWidgetFactory : MainToolbarWidgetFactory, Disposable {

  override fun createWidget(): JComponent {
    val widget = GitToolbarWidget()
    GitWidgetUpdater(getCurrentProject(), widget).subscribe()
    return widget
  }

  override fun dispose() {}

  override fun getPosition(): Position = Position.Left

  private fun getCurrentProject(): Project? = ProjectManager.getInstance().openProjects.firstOrNull()
}

private class GitWidgetUpdater(project: Project?, val widget: GitToolbarWidget) : GitRepositoryChangeListener, GitIncomingOutgoingListener, ProjectManagerListener {

  private val swingExecutor: Executor = Executor { run -> SwingUtilities.invokeLater(run) }

  private var repository: GitRepository? = null
  private var currentProject: Project? = null

  private val INCOMING_CHANGES_ICON = AllIcons.Actions.CheckOut
  private val OUTGOING_CHANGES_ICON = AllIcons.Vcs.Push

  @Volatile
  private var projectConnection: MessageBusConnection? = null

  init {
    currentProject = project
    repository = project?.let { guessCurrentRepo(it) }
    updateWidget()
  }

  fun subscribe() {
    val appConnection = ApplicationManager.getApplication().messageBus.connect(widget)
    appConnection.subscribe(ProjectManager.TOPIC, this)
    currentProject?.let { projectSubscribe(it) }
  }

  private fun projectSubscribe(p: Project) {
    projectConnection?.disconnect()
    val connection = p.messageBus.connect(widget)
    connection.subscribe(GitRepository.GIT_REPO_CHANGE, this)
    connection.subscribe(GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED, this)
    projectConnection = connection
  }

  override fun projectOpened(project: Project) {
    swingExecutor.execute {
      currentProject = project
      repository = guessCurrentRepo(project)
      updateWidget()
    }
    projectSubscribe(project)
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
    widget.project = currentProject
    widget.repository = repository
    widget.text = repository?.currentBranchName ?: GitBundle.message("git.toolbar.widget.no.repo")
    updateIcons()
  }

  private fun updateIcons() {
    val icons = repository?.currentBranchName?.let { branch ->
      val res = mutableListOf<Icon>()
      val incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(currentProject!!)
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