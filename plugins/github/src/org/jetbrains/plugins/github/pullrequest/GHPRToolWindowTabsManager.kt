// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.util.CollectionDelta
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import org.jetbrains.plugins.github.util.GithubGitHelper
import kotlin.properties.Delegates.observable

@Service
internal class GHPRToolWindowTabsManager(private val project: Project) {
  private val gitHelper = GithubGitHelper.getInstance()
  private val contentManager = GHPRToolWindowsTabsContentManager(project, ChangesViewContentManager.getInstance(project))

  private var remoteUrls by observable(setOf<GitRemoteUrlCoordinates>()) { _, oldValue, newValue ->
    val delta = CollectionDelta(oldValue, newValue)
    for (item in delta.removedItems) {
      contentManager.removeTab(item)
    }
    for (item in delta.newItems) {
      contentManager.addTab(item)
    }
  }

  @CalledInAwt
  fun showTab(remoteUrl: GitRemoteUrlCoordinates) {
    if (!remoteUrls.contains(remoteUrl))
      remoteUrls = remoteUrls + remoteUrl
    contentManager.focusTab(remoteUrl)
  }

  init {
    val busConnection = project.messageBus.connect()
    busConnection.subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, VcsRepositoryMappingListener {
      runInEdt {
        removeObsoleteUrls(gitHelper.getPossibleRemoteUrlCoordinates(project))
      }
    })

    busConnection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
      runInEdt {
        removeObsoleteUrls(gitHelper.getPossibleRemoteUrlCoordinates(project))
      }
    })
  }

  private fun removeObsoleteUrls(urls: Set<GitRemoteUrlCoordinates>) {
    remoteUrls = remoteUrls.filter { urls.contains(it) }.toSet()
  }
}
