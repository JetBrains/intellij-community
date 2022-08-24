// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.async.DisposingMainScope
import git4idea.remote.hosting.knownRepositories
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.content.Content
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingPanelFactory
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import java.awt.BorderLayout
import kotlin.properties.Delegates

internal class GHPRToolWindowTabControllerImpl(private val project: Project,
                                               private val authManager: GithubAuthenticationManager,
                                               private val repositoryManager: GHHostedRepositoriesManager,
                                               private val dataContextRepository: GHPRDataContextRepository,
                                               private val projectSettings: GithubPullRequestsProjectUISettings,
                                               private val tab: Content) : GHPRToolWindowTabController {

  private var currentRepository: GHGitRepositoryMapping? = null
  private var currentAccount: GithubAccount? = null

  private val mainPanel = tab.component.apply {
    layout = BorderLayout()
    background = UIUtil.getListBackground()
  }
  private val tabDisposable = Disposer.newCheckedDisposable().also {
    Disposer.register(tab.disposer!!, it)
  }
  private val scope = DisposingMainScope(tabDisposable)

  private val resetRequestFlow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply {
    tryEmit(Unit)
  }

  private var contentDisposable by Delegates.observable<Disposable?>(null) { _, oldValue, newValue ->
    if (oldValue != null) Disposer.dispose(oldValue)
    if (newValue != null) Disposer.register(tabDisposable, newValue)
  }
  private var showingSelectors: Boolean? = null

  override var initialView = GHPRToolWindowViewType.LIST
  override val componentController: GHPRToolWindowTabComponentController?
    get() {
      for (component in mainPanel.components) {
        val controller = UIUtil.getClientProperty(component, GHPRToolWindowTabComponentController.KEY)
        if (controller != null) return controller
      }
      return null
    }

  init {
    scope.launch {
      combine(repositoryManager.knownRepositoriesState, authManager.accountManager.accountsState,
              resetRequestFlow) { repos, accountsMap, _ ->
        Updater(repos, accountsMap.keys)
      }.collectLatest {
        it.update()
      }
    }
  }

  private inner class Updater(private val repos: Set<GHGitRepositoryMapping>,
                              private val accounts: Set<GithubAccount>) {

    fun update() {
      val wasReset = resetIfMissing()
      val repoAndAccount = guessAndSetRepoAndAccount()
      if (repoAndAccount == null) {
        showSelectors()
        return
      }

      val (repo, account) = repoAndAccount
      val requestExecutor = try {
        GithubApiRequestExecutorManager.getInstance().getExecutor(account)
      }
      catch (e: Exception) {
        showSelectors()
        return
      }

      showPullRequestsComponent(repo, account, requestExecutor, wasReset)
    }

    private fun guessAndSetRepoAndAccount(): Pair<GHGitRepositoryMapping, GithubAccount>? {
      val saved = projectSettings.selectedRepoAndAccount
      if (saved != null) {
        currentRepository = saved.first
        currentAccount = saved.second
        return saved
      }

      if (currentRepository == null && repos.size == 1) {
        currentRepository = repos.single()
      }

      val repo = currentRepository
      if (repo != null && currentAccount == null) {
        val matchingAccounts = accounts.filter { it.server.equals(repo.repository.serverPath, true) }
        if (matchingAccounts.size == 1) {
          currentAccount = matchingAccounts.single()
        }
      }
      val account = currentAccount
      return if (repo != null && account != null) repo to account else null
    }

    private fun resetIfMissing(): Boolean {
      var wasReset = false
      val repo = currentRepository
      if (repo != null && !repos.contains(repo)) {
        currentRepository = null
        currentAccount = null
        wasReset = true
      }

      val account = currentAccount
      if (account != null && !accounts.contains(account)) {
        currentAccount = null
        wasReset = true
      }
      return wasReset
    }
  }

  private fun showSelectors() {
    if (showingSelectors == true) return
    val disposable = Disposer.newDisposable()
    contentDisposable = disposable
    tab.displayName = GithubBundle.message("toolwindow.stripe.Pull_Requests")

    val selectorVm = GHPRRepositorySelectorViewModelImpl(project, repositoryManager, authManager).also {
      Disposer.register(disposable, it)
    }

    val uiScope = DisposingMainScope(disposable)

    val component = GHPRRepositorySelectorComponentFactory(selectorVm).create(uiScope)

    uiScope.launch {
      selectorVm.selectionFlow.collect { (repo, account) ->
        currentRepository = repo
        currentAccount = account
        val requestExecutor = GithubApiRequestExecutorManager.getInstance().getExecutor(account, mainPanel) ?: return@collect
        projectSettings.selectedRepoAndAccount = repo to account
        showPullRequestsComponent(repo, account, requestExecutor, false)
        GHUIUtil.focusPanel(mainPanel)
      }
    }
    with(mainPanel) {
      removeAll()
      add(component, BorderLayout.NORTH)
      revalidate()
      repaint()
    }
    showingSelectors = true
  }

  private fun showPullRequestsComponent(repositoryMapping: GHGitRepositoryMapping,
                                        account: GithubAccount,
                                        requestExecutor: GithubApiRequestExecutor,
                                        force: Boolean) {
    if (showingSelectors == false && !force) return
    tab.displayName = GithubBundle.message("toolwindow.stripe.Pull_Requests")

    val repository = repositoryMapping.repository
    val remote = repositoryMapping.remote

    val disposable = Disposer.newDisposable()
    contentDisposable = Disposable {
      Disposer.dispose(disposable)
      dataContextRepository.clearContext(repository)
    }

    val loadingModel = GHCompletableFutureLoadingModel<GHPRDataContext>(disposable).apply {
      future = dataContextRepository.acquireContext(repository, remote, account, requestExecutor)
    }

    val panel = GHLoadingPanelFactory(loadingModel, null, GithubBundle.message("cannot.load.data.from.github"),
                                      GHApiLoadingErrorHandler(project, account) {
                                        val contextRepository = dataContextRepository
                                        contextRepository.clearContext(repository)
                                        loadingModel.future = contextRepository.acquireContext(repository, remote, account, requestExecutor)
                                      }).create { parent, ctx ->
      val wrapper = Wrapper()
      GHPRToolWindowTabComponentControllerImpl(project, repositoryManager, projectSettings, ctx, wrapper, disposable, initialView) {
        tab.displayName = it
      }.also {
        UIUtil.putClientProperty(parent, GHPRToolWindowTabComponentController.KEY, it)
      }
      initialView = GHPRToolWindowViewType.LIST
      wrapper
    }

    with(mainPanel) {
      removeAll()
      add(panel, BorderLayout.CENTER)
      revalidate()
      repaint()
    }
    showingSelectors = false
  }

  override fun canResetRemoteOrAccount(): Boolean {
    if (currentRepository == null) return false
    if (currentAccount == null) return false

    val singleRepo = repositoryManager.knownRepositories.singleOrNull()
    if (singleRepo == null) return true

    val matchingAccounts = authManager.getAccounts().filter { it.server.equals(singleRepo.repository.serverPath, true) }
    return matchingAccounts.size != 1
  }

  override fun resetRemoteAndAccount() {
    currentRepository = null
    currentAccount = null
    projectSettings.selectedRepoAndAccount = null
    resetRequestFlow.tryEmit(Unit)
  }
}
