// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.content.Content
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.AccountTokenChangedListener
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingErrorHandlerImpl
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingPanelFactory
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHProjectRepositoriesManager
import java.awt.BorderLayout
import kotlin.properties.Delegates

internal class GHPRToolWindowTabControllerImpl(private val project: Project,
                                               private val authManager: GithubAuthenticationManager,
                                               private val repositoryManager: GHProjectRepositoriesManager,
                                               private val dataContextRepository: GHPRDataContextRepository,
                                               private val tab: Content) : GHPRToolWindowTabController {

  private var currentRepository: GHGitRepositoryMapping? = null
  private var currentAccount: GithubAccount? = null

  private val mainPanel = tab.component.apply {
    layout = BorderLayout()
    background = UIUtil.getListBackground()
  }
  private var contentDisposable by Delegates.observable<Disposable?>(null) { _, oldValue, newValue ->
    if (oldValue != null) Disposer.dispose(oldValue)
    if (newValue != null) Disposer.register(tab.disposer!!, newValue)
  }
  private var showingSelectors: Boolean? = null

  override val componentController: GHPRToolWindowTabComponentController?
    get() {
      for (component in mainPanel.components) {
        val controller = UIUtil.getClientProperty(component, GHPRToolWindowTabComponentController.KEY)
        if (controller != null) return controller
      }
      return null
    }

  init {
    ApplicationManager.getApplication().messageBus.connect(tab.disposer!!)
      .subscribe(GithubAccountManager.ACCOUNT_TOKEN_CHANGED_TOPIC, object : AccountTokenChangedListener {
        override fun tokenChanged(account: GithubAccount) {
          ApplicationManager.getApplication().invokeLater(Runnable { Updater().update() }) {
            Disposer.isDisposed(tab.disposer!!)
          }
        }
      })
    repositoryManager.addRepositoryListChangedListener(tab.disposer!!) {
      Updater().update()
    }
    Updater().update()
  }

  private inner class Updater {
    private val repos = repositoryManager.knownRepositories
    private val accounts = authManager.getAccounts()

    fun update() {
      resetIfMissing()

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

      if (repo != null && account != null) {
        try {
          val requestExecutor = GithubApiRequestExecutorManager.getInstance().getExecutor(account)
          showPullRequestsComponent(repo, account, requestExecutor)
        }
        catch (e: Exception) {
          //show error near selectors?
        }
      }
      else {
        showSelectors()
      }
    }

    private fun resetIfMissing() {
      val repo = currentRepository
      if (repo != null && !repos.contains(repo)) {
        currentRepository = null
        currentAccount = null
      }

      val account = currentAccount
      if (account != null && !accounts.contains(account)) {
        currentAccount = null
      }
    }
  }

  private fun showSelectors() {
    if (showingSelectors == true) return
    val disposable = Disposer.newDisposable()
    contentDisposable = disposable
    tab.displayName = GithubBundle.message("toolwindow.stripe.Pull_Requests")

    val component = GHPRRepositorySelectorComponentFactory(project, authManager, repositoryManager).create(disposable) { repo, account ->
      currentRepository = repo
      currentAccount = account
      val requestExecutor = GithubApiRequestExecutorManager.getInstance().getExecutor(account, mainPanel) ?: return@create
      showPullRequestsComponent(repo, account, requestExecutor)
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
                                        requestExecutor: GithubApiRequestExecutor) {
    if (showingSelectors == false) return
    tab.displayName = GithubBundle.message("toolwindow.stripe.Pull_Requests")

    val repository = repositoryMapping.repository
    val remote = repositoryMapping.gitRemote

    val disposable = Disposer.newDisposable()
    contentDisposable = Disposable {
      Disposer.dispose(disposable)
      dataContextRepository.clearContext(repository)
    }

    val loadingModel = GHCompletableFutureLoadingModel<GHPRDataContext>(disposable).apply {
      future = dataContextRepository.acquireContext(repository, remote, account, requestExecutor)
    }

    val panel = GHLoadingPanelFactory(loadingModel, null, GithubBundle.message("cannot.load.data.from.github"),
                                      GHLoadingErrorHandlerImpl(project, account) {
                                        val contextRepository = dataContextRepository
                                        contextRepository.clearContext(repository)
                                        loadingModel.future = contextRepository.acquireContext(repository, remote, account, requestExecutor)
                                      }).create { parent, result ->
      val wrapper = Wrapper()
      ComponentController(result, wrapper, disposable).also {
        UIUtil.putClientProperty(parent, GHPRToolWindowTabComponentController.KEY, it)
      }
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

  private inner class ComponentController(private val dataContext: GHPRDataContext,
                                          private val wrapper: Wrapper,
                                          private val parentDisposable: Disposable) : GHPRToolWindowTabComponentController {

    private val listComponent = GHPRListComponent.create(project, dataContext, parentDisposable)
    private var currentDisposable: Disposable? = null

    private var currentPullRequest: GHPRIdentifier? = null

    init {
      viewList()

      DataManager.registerDataProvider(wrapper) { dataId ->
        when {
          GHPRActionKeys.PULL_REQUESTS_TAB_CONTROLLER.`is`(dataId) -> this
          else -> null
        }
      }
    }

    override fun viewList() {
      tab.displayName = GithubBundle.message("tab.title.pull.requests.in", dataContext.gitRemoteCoordinates.remote.name)
      currentDisposable?.let { Disposer.dispose(it) }
      currentPullRequest = null
      wrapper.setContent(listComponent)
      wrapper.repaint()
    }

    override fun refreshList() {
      dataContext.listLoader.reset()
      dataContext.repositoryDataService.resetData()
    }

    override fun viewPullRequest(id: GHPRIdentifier, onShown: ((GHPRViewComponentController?) -> Unit)?) {
      tab.displayName = GithubBundle.message("pull.request.num", id.number)
      if (currentPullRequest != id) {
        currentDisposable?.let { Disposer.dispose(it) }
        currentDisposable = Disposer.newDisposable("Pull request component disposable").also {
          Disposer.register(parentDisposable, it)
        }
        currentPullRequest = id
        val pullRequestComponent = GHPRViewComponentFactory(ActionManager.getInstance(), project, dataContext, this, id,
                                                            currentDisposable!!)
          .create()
        wrapper.setContent(pullRequestComponent)
        wrapper.repaint()
      }
      if (onShown == null) GHUIUtil.focusPanel(wrapper.targetComponent)
      else onShown(UIUtil.getClientProperty(wrapper.targetComponent, GHPRViewComponentController.KEY))
    }

    override fun openPullRequestTimeline(id: GHPRIdentifier, requestFocus: Boolean) =
      dataContext.filesManager.createAndOpenTimelineFile(id, requestFocus)

    override fun openPullRequestDiff(id: GHPRIdentifier, requestFocus: Boolean) =
      dataContext.filesManager.createAndOpenDiffFile(id, requestFocus)
  }
}