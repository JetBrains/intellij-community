// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.util.CollectionDelta
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import org.jetbrains.plugins.github.util.GithubGitHelper
import kotlin.properties.Delegates.observable

@Service
internal class GHPRToolWindowTabsManager(private val project: Project) {
  private val gitHelper = GithubGitHelper.getInstance()
  private val settings = GithubPullRequestsProjectUISettings.getInstance(project)

  private val contentManager by lazy(LazyThreadSafetyMode.NONE) {
    GHPRToolWindowsTabsContentManager(project, ChangesViewContentManager.getInstance(project))
  }

  private var remoteUrls by observable(setOf<GitRemoteUrlCoordinates>()) { _, oldValue, newValue ->
    val delta = CollectionDelta(oldValue, newValue)
    for (item in delta.removedItems) {
      contentManager.removeTab(item)
    }
    for (item in delta.newItems) {
      contentManager.addTab(item, Disposable {
        //means that tab closed by user
        if (gitHelper.getPossibleRemoteUrlCoordinates(project).contains(item)) settings.addHiddenUrl(item.url)
        ApplicationManager.getApplication().invokeLater(::updateRemoteUrls) { project.isDisposedOrDisposeInProgress }
      })
    }
  }

  @CalledInAwt
  fun showTab(remoteUrl: GitRemoteUrlCoordinates) {
    settings.removeHiddenUrl(remoteUrl.url)
    updateRemoteUrls()

    contentManager.focusTab(remoteUrl)
  }

  private fun updateRemoteUrls() {
    remoteUrls = gitHelper.getPossibleRemoteUrlCoordinates(project).filter {
      !settings.getHiddenUrls().contains(it.url)
    }.toSet()
  }

  class RemoteUrlsListener(private val project: Project)
    : VcsRepositoryMappingListener, GitRepositoryChangeListener {

    override fun mappingChanged() = updateRemotes()
    override fun repositoryChanged(repository: GitRepository) = updateRemotes()

    private fun updateRemotes() {
      val application = ApplicationManager.getApplication()
      if (application.isDispatchThread) {
        project.service<GHPRToolWindowTabsManager>().updateRemoteUrls()
      }
      else {
        application.invokeLater(::updateRemotes) { project.isDisposedOrDisposeInProgress }
      }
    }
  }
}
