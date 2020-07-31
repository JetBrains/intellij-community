// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.wm.ToolWindowManager
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.authentication.accounts.AccountRemovedListener
import org.jetbrains.plugins.github.authentication.accounts.AccountTokenChangedListener
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.util.CollectionDelta
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import org.jetbrains.plugins.github.util.GithubGitHelper
import kotlin.properties.Delegates.observable

@Service
internal class GHPRToolWindowTabsManager(private val project: Project) {
  private val gitHelper = GithubGitHelper.getInstance()
  private val settings = GithubPullRequestsProjectUISettings.getInstance(project)

  private val tabDisposalListener = object : GHPRToolWindowTabsContentManager.TabDisposalListener {

    var muted = false

    override fun tabDisposed(remoteUrl: GitRemoteUrlCoordinates) {
      if (!muted) {
        if (gitHelper.getPossibleRemoteUrlCoordinates(project).contains(remoteUrl)) settings.addHiddenUrl(remoteUrl.url)
        updateTabs()
      }
    }
  }

  internal var contentManager: GHPRToolWindowTabsContentManager?
    by observable<GHPRToolWindowTabsContentManager?>(null) { _, oldManager, newManager ->
      oldManager?.removeTabDisposalEventListener(tabDisposalListener)
      newManager?.addTabDisposalEventListener(tabDisposalListener)
      updateTabs()
    }

  @CalledInAwt
  fun isAvailable(): Boolean = getRemoteUrls().isNotEmpty()

  @CalledInAwt
  fun showTab(remoteUrl: GitRemoteUrlCoordinates) {
    settings.removeHiddenUrl(remoteUrl.url)
    updateTabs {
      ToolWindowManager.getInstance(project).getToolWindow(GHPRToolWindowFactory.ID)?.show {
        contentManager?.focusTab(remoteUrl)
      }
    }
  }

  private fun updateTabs(afterUpdate: (() -> Unit)? = null) {
    val currentUrls = getRemoteUrls()

    ToolWindowManager.getInstance(project).getToolWindow(GHPRToolWindowFactory.ID)?.setAvailable(currentUrls.isNotEmpty()) {
      val contentManager = contentManager
      if (contentManager != null) {
        val delta = CollectionDelta(contentManager.currentTabs, currentUrls)
        for (item in delta.removedItems) {
          contentManager.removeTab(item)
        }
        for (item in delta.newItems) {
          contentManager.addTab(item)
        }
      }
      afterUpdate?.invoke()
    }
  }

  private fun getRemoteUrls(): Set<GitRemoteUrlCoordinates> {
    return gitHelper.getPossibleRemoteUrlCoordinates(project).filter {
      !settings.getHiddenUrls().contains(it.url)
    }.toSet()
  }

  class RemoteUrlsListener(private val project: Project)
    : VcsRepositoryMappingListener, GitRepositoryChangeListener {

    override fun mappingChanged() = runInEdt(project) { updateRemotes(project) }
    override fun repositoryChanged(repository: GitRepository) = runInEdt(project) { updateRemotes(project) }
  }

  class AccountsListener : AccountRemovedListener, AccountTokenChangedListener {
    override fun accountRemoved(removedAccount: GithubAccount) = updateRemotes()
    override fun tokenChanged(account: GithubAccount) = updateRemotes()

    private fun updateRemotes() = runInEdt {
      for (project in ProjectManager.getInstance().openProjects) {
        updateRemotes(project)
      }
    }
  }

  class BeforePluginUnloadListener(private val project: Project) : DynamicPluginListener {
    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
      if (pluginDescriptor.pluginId == PluginManager.getInstance().getPluginOrPlatformByClassName(this::class.java.name)) {
        muteTabDisposalListener(project)
      }
    }
  }

  class BeforeProjectCloseListener : ProjectManagerListener {
    override fun projectClosing(project: Project) = muteTabDisposalListener(project)
  }

  companion object {
    private inline fun runInEdt(project: Project, crossinline runnable: () -> Unit) {
      val application = ApplicationManager.getApplication()
      if (application.isDispatchThread) runnable()
      else application.invokeLater({ runnable() }) { project.isDisposed }
    }

    private fun updateRemotes(project: Project) = project.service<GHPRToolWindowTabsManager>().updateTabs()

    private fun muteTabDisposalListener(project: Project) {
      project.service<GHPRToolWindowTabsManager>().tabDisposalListener.muted = true
    }
  }
}
