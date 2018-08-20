// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerAdapter
import com.intellij.ui.content.ContentManagerEvent
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import icons.GithubIcons
import org.jetbrains.plugins.github.authentication.accounts.AccountTokenChangedListener
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
import org.jetbrains.plugins.github.pullrequest.ui.GithubPullRequestsComponentFactory
import org.jetbrains.plugins.github.util.GithubUrlUtil

const val TOOL_WINDOW_ID = "GitHub Pull Requests"

private val REPOSITORY_KEY = Key<GitRepository>("REPOSITORY")
private val REMOTE_KEY = Key<GitRemote>("REMOTE")
private val REMOTE_URL_KEY = Key<String>("REMOTE_URL")
private val ACCOUNT_KEY = Key<GithubAccount>("ACCOUNT")

class GithubPullRequestsToolWindowManager internal constructor(private val project: Project,
                                                               private val toolWindowManager: ToolWindowManager,
                                                               private val gitRepositoryManager: GitRepositoryManager,
                                                               private val accountManager: GithubAccountManager,
                                                               private val componentFactory: GithubPullRequestsComponentFactory) {

  fun showPullRequestsTab(repository: GitRepository, remote: GitRemote, remoteUrl: String, account: GithubAccount) {
    val toolWindow = getCurrentOrRegisterNewToolWindow()
    val contentManager = toolWindow.contentManager

    var content = contentManager.findContentByRemoteUrlInContent(remoteUrl)
    if (content == null) {
      val component = componentFactory.createComponent(remoteUrl, account) ?: return
      content = contentManager.factory.createContent(component, null, false)
        .apply {
          setPreferredFocusedComponent { component }
          isCloseable = true
          displayName = GithubUrlUtil.removeProtocolPrefix(remoteUrl)

          putUserData(REPOSITORY_KEY, repository)
          putUserData(REMOTE_KEY, remote)
          putUserData(REMOTE_URL_KEY, remoteUrl)
          putUserData(ACCOUNT_KEY, account)
        }
      contentManager.addContent(content)
    }

    contentManager.setSelectedContent(content, true)
    toolWindow.show { }
  }

  private fun getCurrentOrRegisterNewToolWindow(): ToolWindow {
    return toolWindowManager.getToolWindow(TOOL_WINDOW_ID)
           ?: toolWindowManager.registerToolWindow(TOOL_WINDOW_ID, true, ToolWindowAnchor.BOTTOM, project, true)
             .apply {
               icon = GithubIcons.PullRequestsToolWindow
               contentManager.addContentManagerListener(object : ContentManagerAdapter() {
                 override fun contentRemoved(event: ContentManagerEvent?) {
                   if (contentManager.contentCount == 0) unregisterToolWindow()
                 }
               })
             }
  }

  private fun unregisterToolWindow() = toolWindowManager.unregisterToolWindow(TOOL_WINDOW_ID)

  init {
    val busConnection = project.messageBus.connect()
    busConnection.subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, VcsRepositoryMappingListener {
      runInEdt {
        val toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID) ?: return@runInEdt
        val repositories = gitRepositoryManager.repositories
        if (repositories.isEmpty()) {
          unregisterToolWindow()
          return@runInEdt
        }
        removeContentsForRemovedRepositories(toolWindow, repositories)
      }
    })

    busConnection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { repository ->
      runInEdt {
        val toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID) ?: return@runInEdt
        removeContentsForRemovedRemotes(toolWindow, repository)
        removeContentsForRemovedRemoteUrls(toolWindow, repository)
      }
    })

    busConnection.subscribe(GithubAccountManager.ACCOUNT_TOKEN_CHANGED_TOPIC, object : AccountTokenChangedListener {
      override fun tokenChanged(account: GithubAccount) {
        runInEdt {
          val toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID) ?: return@runInEdt
          if (accountManager.getTokenForAccount(account) == null) removeContentsUsingRemovedAccount(toolWindow, account)
        }
      }
    })
  }

  private fun removeContentsForRemovedRepositories(toolWindow: ToolWindow, repositories: List<GitRepository>) {
    findAndRemoveContents(toolWindow) {
      !repositories.contains(it.getUserData(REPOSITORY_KEY))
    }
  }

  private fun removeContentsForRemovedRemotes(toolWindow: ToolWindow, repository: GitRepository) {
    findAndRemoveContents(toolWindow) {
      it.getUserData(REPOSITORY_KEY) == repository && !repository.remotes.contains(it.getUserData(REMOTE_KEY))
    }
  }

  private fun removeContentsForRemovedRemoteUrls(toolWindow: ToolWindow, repository: GitRepository) {
    val urls = repository.remotes.map { it.urls }.flatten().toSet()
    findAndRemoveContents(toolWindow) {
      it.getUserData(REPOSITORY_KEY) == repository && !urls.contains(it.getUserData(REMOTE_URL_KEY))
    }
  }

  private fun findAndRemoveContents(toolWindow: ToolWindow, predicate: (Content) -> Boolean) {
    val contentManager = toolWindow.contentManager
    for (content in contentManager.contents) {
      if (predicate(content))
        contentManager.removeContent(content, true)
    }
  }

  private fun removeContentsUsingRemovedAccount(toolWindow: ToolWindow, removedAccount: GithubAccount) {
    val contentManager = toolWindow.contentManager
    for (content in contentManager.contents) {
      val account = content.getUserData(ACCOUNT_KEY)
      if (account == removedAccount) contentManager.removeContent(content, true)
    }
  }

  private fun ContentManager.findContentByRemoteUrlInContent(remoteUrl: String) =
    contents.find { it.getUserData(REMOTE_URL_KEY) == remoteUrl }
}
