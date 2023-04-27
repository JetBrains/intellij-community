// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.async.DisposingMainScope
import com.intellij.collaboration.async.DisposingScope
import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.util.URIUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.util.childScope
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.plugins.github.api.GHRepositoryConnection
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.ui.selector.GHRepositoryAndAccountSelectorComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.selector.GHRepositoryAndAccountSelectorViewModel
import org.jetbrains.plugins.github.pullrequest.ui.selector.GHRepositoryConnectionManager
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import java.awt.BorderLayout
import java.util.concurrent.CompletableFuture
import javax.swing.JPanel

internal class MultiTabGHPRToolWindowContentController(parentDisposable: Disposable,
                                                       private val project: Project,
                                                       private val repositoriesManager: GHHostedRepositoriesManager,
                                                       private val accountManager: GHAccountManager,
                                                       private val connectionManager: GHRepositoryConnectionManager,
                                                       private val settings: GithubPullRequestsProjectUISettings,
                                                       private val toolwindow: ToolWindow)
  : GHPRToolWindowLoginController, GHPRToolWindowContentController {

  private val cs = DisposingScope(parentDisposable, SupervisorJob() + Dispatchers.Main.immediate)
  private val contentManager = toolwindow.contentManager

  override val loginController: GHPRToolWindowLoginController = this
  private val singleRepoAndAccountState: StateFlow<Pair<GHGitRepositoryMapping, GithubAccount>?> =
    combineState(cs, repositoriesManager.knownRepositoriesState, accountManager.accountsState) { repos, accounts ->
      repos.singleOrNull()?.let { repo ->
        accounts.singleOrNull { URIUtil.equalWithoutSchema(it.server.toURI(), repo.repository.serverPath.toURI()) }?.let {
          repo to it
        }
      }
    }

  private var _contentController = CompletableFuture<GHPRToolWindowRepositoryContentController>()
  override val repositoryContentController: CompletableFuture<GHPRToolWindowRepositoryContentController>
    get() = _contentController

  init {
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      var initial = true
      var requestFocus = false
      connectionManager.connectionState.collectLatest { conn ->
        withContext(NonCancellable) {
          if (conn == null) {
            if (!initial) {
              _contentController.cancel(true)
              _contentController = CompletableFuture()
            }
            showSelectorsTab(requestFocus)
          }
          else {
            val controller = showRepositoryContent(conn, requestFocus)
            _contentController.complete(controller)
            initial = false
          }
        }

        try {
          awaitCancellation()
        }
        finally {
          withContext(NonCancellable) {
            requestFocus = contentManager.selectedContent?.let {
              CollaborationToolsUIUtil.isFocusParent(it.component)
            } ?: false
            contentManager.removeAllContents(true)
          }
        }
      }
    }

    contentManager.addDataProvider { dataId ->
      when {
        GHPRActionKeys.PULL_REQUESTS_CONTENT_CONTROLLER.`is`(dataId) -> _contentController.getNow(null)
        else -> null
      }
    }
  }

  private fun showSelectorsTab(requestFocus: Boolean) {
    val contentDisposer = Disposer.newDisposable()
    val cs = DisposingMainScope(contentDisposer)
    val selectorVm = GHRepositoryAndAccountSelectorViewModel(cs, repositoriesManager, accountManager, ::connect)

    settings.selectedRepoAndAccount?.let { (repo, account) ->
      with(selectorVm) {
        repoSelectionState.value = repo
        accountSelectionState.value = account
        submitSelection()
      }
    }

    cs.launch {
      singleRepoAndAccountState.collect {
        if (it != null) {
          with(selectorVm) {
            repoSelectionState.value = it.first
            accountSelectionState.value = it.second
            submitSelection()
          }
        }
      }
    }

    val selector = GHRepositoryAndAccountSelectorComponentFactory(project, selectorVm, accountManager).create(cs.childScope())
    val component = JPanel(BorderLayout()).apply {
      background = UIUtil.getListBackground()
      add(selector, BorderLayout.NORTH)
    }
    val content = contentManager.factory.createContent(component, GithubBundle.message("toolwindow.stripe.Pull_Requests"), false).apply {
      isCloseable = false
      isPinned = true
      setDisposer(contentDisposer)
    }
    contentManager.addContent(content)
    contentManager.setSelectedContent(content, requestFocus)
  }

  private suspend fun connect(repo: GHGitRepositoryMapping, account: GithubAccount) {
    withContext(cs.coroutineContext) {
      connectionManager.openConnection(repo, account)
      settings.selectedRepoAndAccount = repo to account
    }
  }

  private fun showRepositoryContent(conn: GHRepositoryConnection, focused: Boolean): GHPRToolWindowRepositoryContentController {
    return MultiTabGHPRToolWindowRepositoryContentController(
      project,
      repositoriesManager,
      settings,
      conn.dataContext,
      toolwindow).apply {
      viewList(focused)
    }
  }

  override fun canResetRemoteOrAccount(): Boolean {
    return connectionManager.connectionState.value != null && singleRepoAndAccountState.value == null
  }

  override fun resetRemoteAndAccount() {
    cs.launch {
      settings.selectedRepoAndAccount = null
      connectionManager.closeConnection()
    }
  }
}
