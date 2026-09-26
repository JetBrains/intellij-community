// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.dvcs.ui.DvcsStatusWidget
import com.intellij.ide.navigationToolbar.rider.RiderMainToolbarStateListener
import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetSettings
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.ExperimentalUI
import com.intellij.util.messages.MessageBusConnection
import com.intellij.vcs.git.GitDisplayName
import com.intellij.vcs.git.branch.calcTooltip
import git4idea.GitBranchesUsageCollector.branchWidgetClicked
import git4idea.GitUtil
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchIncomingOutgoingManager.GitIncomingOutgoingListener
import git4idea.branch.GitBranchUtil
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.BranchIconUtil.Companion.getBranchIcon
import git4idea.ui.branch.popup.GitBranchesTreePopupOnBackend
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

private const val ID: @NonNls String = "git"

/**
 * A status bar widget which displays the current branch for a file currently open in the editor.
 */
// used externally
open class GitBranchWidget(project: Project) : DvcsStatusWidget<GitRepository>(project, GitDisplayName.NAME) {
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
    return GitBranchesTreePopupOnBackend.create(project, repository)
  }

  override fun rememberRecentRoot(path: String) {
    GitVcsSettings.getInstance(project).setRecentRoot(path)
  }

  override fun getToolTip(repository: GitRepository?): @NlsContexts.Tooltip String? {
    return if (repository != null && repository.state == Repository.State.DETACHED) {
      GitBundle.message("git.status.bar.widget.tooltip.detached")
    }
    else {
      repository ?: return null
      val toolTip = super.getToolTip(repository) ?: return null
      val htmlBuilder = HtmlBuilder().append(toolTip)

      val currentBranch = repository.currentBranch ?: return htmlBuilder.toString()
      val incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(project)
      val incomingOutgoingState = incomingOutgoingManager.getIncomingOutgoingState(repository, currentBranch)
      val incomingOutgoingTooltip = incomingOutgoingState.calcTooltip()
      if (incomingOutgoingTooltip != null) {
        htmlBuilder.br()
        htmlBuilder.appendRaw(incomingOutgoingTooltip)
      }

      htmlBuilder.toString()
    }
  }

  internal class Listener(private val project: Project) : VcsRepositoryMappingListener {
    override fun mappingChanged() {
      project.service<StatusBarWidgetsManager>().updateWidget(Factory::class.java)
    }
  }

  internal class SettingsListener(private val project: Project) : UISettingsListener {
    override fun uiSettingsChanged(uiSettings: UISettings) {
      val statusBarSettings = StatusBarWidgetSettings.getInstance()
      if (!ExperimentalUI.isNewUI() || statusBarSettings.isExplicitlyDisabled(ID)) return

      // Show/hide git branch if main toolbar is hidden/shown via settings
      StatusBarWidgetFactory.EP_NAME.findExtension(Factory::class.java)?.let {  factory ->
        val manager = project.service<StatusBarWidgetsManager>()
        if (manager.wasWidgetCreated(ID) != factory.isEnabledByDefault) {
          manager.updateWidget(factory)
        }
      }
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
      if (ExperimentalUI.isNewUI()) {
        // Show by default if the main toolbar is hidden via settings
        return !UISettings.getInstance().showNewMainToolbar
      }

      val toolbarSettings = ToolbarSettings.getInstance()
      return !toolbarSettings.isVisible || !toolbarSettings.isAvailable
    }
  }

  internal class MyRiderMainToolbarStateListener(private val project: Project) : RiderMainToolbarStateListener {
    override fun refreshVisibility() {
      project.getService(StatusBarWidgetsManager::class.java).updateWidget(Factory::class.java)
    }
  }
}
