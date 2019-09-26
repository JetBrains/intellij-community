// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.vcsUtil.VcsImplUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.authentication.accounts.AccountRemovedListener
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates

@Service
internal class GHPRToolWindowManager(private val project: Project) {
  private val changesViewContentManager = ChangesViewContentManager.getInstance(project)

  @CalledInAwt
  fun createPullRequestsTab(remoteUrl: GitRemoteUrlCoordinates,
                            account: GithubAccount,
                            requestExecutor: GithubApiRequestExecutor) {
    if (!changesViewContentManager.isAvailable) return
    changesViewContentManager.addContent(createContent(remoteUrl, account, requestExecutor))
    updateTabNames()
  }

  private fun updateTabNames() {
    val contents = changesViewContentManager.findContents { it.getUserData(PARAMETERS_KEY) != null }
    if (contents.size == 1) contents.single().displayName = GROUP_PREFIX
    else {
      // prefix with root name if there are duplicate remote names
      val prefixRoot = contents.map { it.getUserData(PARAMETERS_KEY)!!.remoteUrl.remote }.groupBy { it.name }.values.any { it.size > 1 }
      for (content in contents) {
        val remoteUrl = content.getUserData(PARAMETERS_KEY)!!.remoteUrl
        if (prefixRoot) {
          val shortRootName = VcsImplUtil.getShortVcsRootName(project, remoteUrl.repository.root)
          content.displayName = "$GROUP_PREFIX: $shortRootName/${remoteUrl.remote.name}"
        }
        else {
          content.displayName = "$GROUP_PREFIX: ${remoteUrl.remote.name}"
        }
      }
    }
  }

  @CalledInAwt
  fun showPullRequestsTabIfExists(remoteUrl: GitRemoteUrlCoordinates, account: GithubAccount): Boolean {
    if (!changesViewContentManager.isAvailable) return false

    val content = changesViewContentManager.findContents {
      it.getUserData(PARAMETERS_KEY) == Parameters(remoteUrl, account)
    }.firstOrNull() ?: return false

    ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)?.show {
      changesViewContentManager.setSelectedContent(content, true)
    }
    return true
  }

  private fun createContent(remoteUrl: GitRemoteUrlCoordinates,
                            account: GithubAccount,
                            requestExecutor: GithubApiRequestExecutor): Content {
    val disposable = Disposer.newDisposable().also {
      Disposer.register(it, Disposable { updateTabNames() })
    }
    val component = project.service<GHPRComponentFactory>().createComponent(remoteUrl, account, requestExecutor, disposable)

    return ContentFactory.SERVICE.getInstance().createContent(component, GROUP_PREFIX, false).apply {
      isCloseable = true
      disposer = disposable
      putUserData(PARAMETERS_KEY, Parameters(remoteUrl, account))
      putUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY, ChangesViewContentManager.TabOrderWeight.LAST.weight)
    }
  }

  init {
    val busConnection = project.messageBus.connect()
    busConnection.subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, VcsRepositoryMappingListener {
      runInEdt {
        val repositories = GitRepositoryManager.getInstance(project).repositories
        removeContentsForRemovedRepositories(repositories)
      }
    })

    busConnection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { repository ->
      runInEdt {
        removeContentsForRemovedRemotes(repository)
        removeContentsForRemovedRemoteUrls(repository)
      }
    })

    busConnection.subscribe(GithubAccountManager.ACCOUNT_REMOVED_TOPIC, object : AccountRemovedListener {
      override fun accountRemoved(removedAccount: GithubAccount) {
        runInEdt {
          removeContentsUsingRemovedAccount(removedAccount)
        }
      }
    })
  }

  private fun removeContentsForRemovedRepositories(repositories: List<GitRepository>) {
    findAndRemoveContents {
      !repositories.contains(it.remoteUrl.repository)
    }
  }

  private fun removeContentsForRemovedRemotes(repository: GitRepository) {
    findAndRemoveContents {
      it.remoteUrl.repository == repository && !repository.remotes.contains(it.remoteUrl.remote)
    }
  }

  private fun removeContentsForRemovedRemoteUrls(repository: GitRepository) {
    val urls = repository.remotes.map { it.urls }.flatten().toSet()
    findAndRemoveContents {
      it.remoteUrl.repository == repository && !urls.contains(it.remoteUrl.url)
    }
  }

  private fun removeContentsUsingRemovedAccount(removedAccount: GithubAccount) {
    findAndRemoveContents {
      removedAccount == it.account
    }
  }

  private fun findAndRemoveContents(predicate: (Parameters) -> Boolean) {
    val contents = changesViewContentManager.findContents {
      val parameters = it.getUserData(PARAMETERS_KEY)
      parameters != null && predicate(parameters)
    }
    for (content in contents) {
      changesViewContentManager.removeContent(content)
    }
    updateTabNames()
  }

  companion object {
    @Nls
    private const val GROUP_PREFIX = "Pull Requests"

    private data class Parameters(val remoteUrl: GitRemoteUrlCoordinates, val account: GithubAccount) {}

    private val PARAMETERS_KEY = Key<Parameters>("GHPR_PARAMETERS")
  }
}
