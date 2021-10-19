// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.toolbar

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
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbarWidgetFactory.*
import com.intellij.openapi.wm.impl.headertoolbar.ObservableValue
import com.intellij.util.messages.MessageBusConnection
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchIncomingOutgoingManager.GitIncomingOutgoingListener
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.ui.branch.GitBranchPopup
import java.awt.event.ActionListener
import javax.swing.Icon
import javax.swing.JComponent

internal data class Changes(val incoming: Boolean, val outgoing: Boolean)

class GitToolbarWidgetFactory : MainToolbarWidgetFactory, Disposable {

  private val branchObservable = ObservableValue<String?>(null)
  private val repositoryObservable = ObservableValue<GitRepository?>(null)
  private val changesObservable = ObservableValue<Changes>(Changes(false, false))
  private var vcsInfoConnection: MessageBusConnection? = null
  private var currentProject: Project? = null

  private val projectListener = object: ProjectManagerListener {
    override fun projectOpened(project: Project) {
      currentProject = project
      vcsInfoConnection?.disconnect()
      vcsInfoConnection = project.messageBus.connect(this@GitToolbarWidgetFactory)

      vcsInfoConnection?.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { repo ->
        repositoryObservable.value = repo
        branchObservable.value = repo.currentBranchName
      })

      vcsInfoConnection?.subscribe(GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED, GitIncomingOutgoingListener {
        val manager = GitBranchIncomingOutgoingManager.getInstance(project)
        branchObservable.value?.let { branch ->
          val incoming = manager.hasIncomingFor(repositoryObservable.value, branch)
          val outgoing = manager.hasOutgoingFor(repositoryObservable.value, branch)
          changesObservable.value = Changes(incoming, outgoing)
        }
      })
    }
  }

  init {
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(ProjectManager.TOPIC, projectListener)
  }

  override fun createWidget(): JComponent {
    val widget = GitToolbarWidget(branchObservable, changesObservable)
    widget.leftIcons = listOf(AllIcons.Vcs.Branch)
    widget.addPressListener(ActionListener {
      val repository = repositoryObservable.value
      if (currentProject == null || repository == null) return@ActionListener
      val dataManager = DataManager.getInstance()
      val listPopup = GitBranchPopup.getInstance(currentProject!!, repository, dataManager.getDataContext(widget)).asListPopup()
      listPopup.showUnderneathOf(widget)
    })
    return widget
  }

  override fun dispose() {}

  override fun getPosition(): Position = Position.Left
}

class GitToolbarWidget internal constructor(branchObservable: ObservableValue<String?>,
  changesObservable: ObservableValue<Changes>) : ToolbarComboWidget(), Disposable {

  val INCOMING_CHANGES_ICON = AllIcons.Actions.CheckOut
  val OUTGOING_CHANGES_ICON = AllIcons.Vcs.Push

  init {
    val branchSubscription = branchObservable.subscribe { text = it }
    val changesSubscription = changesObservable.subscribe { updateRightIcons(it) }
    Disposer.register(this, branchSubscription)
    Disposer.register(this, changesSubscription)
  }

  override fun dispose() {}

  private fun updateRightIcons(changes: Changes) {
    val res = mutableListOf<Icon>()
    if (changes.incoming) res.add(INCOMING_CHANGES_ICON)
    if (changes.outgoing) res.add(OUTGOING_CHANGES_ICON)

    rightIcons = res
  }
}