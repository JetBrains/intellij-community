// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.dvcs.ui.DvcsStatusWidget
import com.intellij.ide.DataManager
import com.intellij.ide.navigationToolbar.experimental.ExperimentalToolbarStateListener
import com.intellij.ide.ui.ToolbarSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.ExperimentalUI
import com.intellij.util.messages.MessageBusConnection
import git4idea.GitBranchesUsageCollector.Companion.branchWidgetClicked
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchIncomingOutgoingManager.GitIncomingOutgoingListener
import git4idea.branch.GitBranchUtil
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.BranchIconUtil.Companion.getBranchIcon
import git4idea.ui.branch.GitBranchesTreePopup.Companion.create
import git4idea.ui.branch.GitBranchesTreePopup.Companion.isEnabled
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

private const val ID: @NonNls String = "git"

/**
 * A status bar widget which displays the current branch for a file currently open in the editor.
 */
// used externally
open class GitBranchWidget(project: Project) : DvcsStatusWidget<GitRepository>(project, GitVcs.DISPLAY_NAME.get()) {
  override fun registerCustomListeners(connection: MessageBusConnection) {
    super.registerCustomListeners(connection)

    connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { updateLater() })
    connection.subscribe(GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED, GitIncomingOutgoingListener { updateLater() })
  }

  override fun ID(): String = ID

  override fun copy(): StatusBarWidget = GitBranchWidget(project)

  override fun guessCurrentRepository(project: Project, selectedFile: VirtualFile?): GitRepository? {
    return GitBranchUtil.guessWidgetRepository(project, selectedFile)
  }

  override fun getIcon(repository: GitRepository): Icon = getBranchIcon(repository)

  override fun getFullBranchName(repository: GitRepository): String = GitBranchUtil.getDisplayableBranchText(repository)

  override fun isMultiRoot(project: Project): Boolean = !GitUtil.justOneGitRepository(project)

  override fun getWidgetPopup(project: Project, repository: GitRepository): JBPopup {
    branchWidgetClicked()
    return if (isEnabled()) {
      create(project)
    }
    else {
      GitBranchPopup.getInstance(project, repository, DataManager.getInstance().getDataContext(myStatusBar!!.component)).asListPopup()
    }
  }

  override fun rememberRecentRoot(path: String) {
    GitVcsSettings.getInstance(project).setRecentRoot(path)
  }

  override fun getToolTip(repository: GitRepository?): @NlsContexts.Tooltip String? {
    return if (repository != null && repository.state == Repository.State.DETACHED) {
      GitBundle.message("git.status.bar.widget.tooltip.detached")
    }
    else {
      super.getToolTip(repository)
    }
  }

  internal class Listener(private val project: Project) : VcsRepositoryMappingListener {
    override fun mappingChanged() {
      project.service<StatusBarWidgetsManager>().updateWidget(Factory::class.java)
    }
  }

  internal class Factory : StatusBarWidgetFactory {
    override fun getId(): String = ID

    override fun getDisplayName(): String = GitBundle.message("git.status.bar.widget.name")

    override fun isAvailable(project: Project): Boolean {
      return (ExperimentalUI.isNewUI() || isEnabledByDefault) && !GitRepositoryManager.getInstance(project).repositories.isEmpty()
    }

    override fun createWidget(project: Project): StatusBarWidget = GitBranchWidget(project)

    override fun isEnabledByDefault(): Boolean {
      // disabled by default in ExperimentalUI per designers request
      if (ExperimentalUI.isNewUI()) {
        return false
      }

      val toolbarSettings = ToolbarSettings.getInstance()
      return !toolbarSettings.isVisible || !toolbarSettings.isAvailable
    }
  }

  internal class MyExperimentalToolbarStateListener(private val project: Project) : ExperimentalToolbarStateListener {
    override fun refreshVisibility() {
      project.getService(StatusBarWidgetsManager::class.java).updateWidget(Factory::class.java)
    }
  }
}