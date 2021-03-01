// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
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
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingPanelFactory
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateComponentFactory
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHProjectRepositoriesManager
import java.awt.BorderLayout
import kotlin.properties.Delegates

internal class GHPRToolWindowTabControllerImpl(private val project: Project,
                                               private val authManager: GithubAuthenticationManager,
                                               private val repositoryManager: GHProjectRepositoriesManager,
                                               private val dataContextRepository: GHPRDataContextRepository,
                                               private val projectSettings: GithubPullRequestsProjectUISettings,
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

  override var initialView = GHPRToolWindowInitialView.LIST
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
      val wasReset = resetIfMissing()
      guessAndSetRepoAndAccount()?.let { (repo, account) ->
        try {
          val requestExecutor = GithubApiRequestExecutorManager.getInstance().getExecutor(account)
          showPullRequestsComponent(repo, account, requestExecutor, wasReset)
        }
        catch (e: Exception) {
          null
        }
      } ?: showSelectors()
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

    val component = GHPRRepositorySelectorComponentFactory(project, authManager, repositoryManager).create(disposable) { repo, account ->
      currentRepository = repo
      currentAccount = account
      val requestExecutor = GithubApiRequestExecutorManager.getInstance().getExecutor(account, mainPanel) ?: return@create
      projectSettings.selectedRepoAndAccount = repo to account
      showPullRequestsComponent(repo, account, requestExecutor, false)
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
                                      GHApiLoadingErrorHandler(project, account) {
                                        val contextRepository = dataContextRepository
                                        contextRepository.clearContext(repository)
                                        loadingModel.future = contextRepository.acquireContext(repository, remote, account, requestExecutor)
                                      }).create { parent, result ->
      val wrapper = Wrapper()
      ComponentController(result, wrapper, disposable).also {
        UIUtil.putClientProperty(parent, GHPRToolWindowTabComponentController.KEY, it)
      }
      initialView = GHPRToolWindowInitialView.LIST
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
    Updater().update()
  }

  private inner class ComponentController(private val dataContext: GHPRDataContext,
                                          private val wrapper: Wrapper,
                                          private val parentDisposable: Disposable) : GHPRToolWindowTabComponentController {

    private val listComponent by lazy { GHPRListComponent.create(project, dataContext, parentDisposable) }
    private val createComponent = ClearableLazyValue.create {
      GHPRCreateComponentFactory(ActionManager.getInstance(), project, projectSettings, repositoryManager, dataContext, this,
                                 parentDisposable)
        .create()
    }
    private var currentDisposable: Disposable? = null

    private var currentPullRequest: GHPRIdentifier? = null

    init {
      when (initialView) {
        GHPRToolWindowInitialView.LIST -> viewList(false)
        GHPRToolWindowInitialView.NEW -> createPullRequest(false)
      }

      DataManager.registerDataProvider(wrapper) { dataId ->
        when {
          GHPRActionKeys.PULL_REQUESTS_TAB_CONTROLLER.`is`(dataId) -> this
          else -> null
        }
      }
    }

    override fun createPullRequest(requestFocus: Boolean) {
      tab.displayName = GithubBundle.message("tab.title.pull.requests.new")
      currentDisposable?.let { Disposer.dispose(it) }
      currentPullRequest = null
      wrapper.setContent(createComponent.value)
      wrapper.repaint()
      if (requestFocus) GHUIUtil.focusPanel(wrapper.targetComponent)
    }

    override fun resetNewPullRequestView() {
      createComponent.drop()
    }

    override fun viewList(requestFocus: Boolean) {
      val allRepos = repositoryManager.knownRepositories.map(GHGitRepositoryMapping::repository)
      tab.displayName = GithubBundle.message("tab.title.pull.requests.in",
                                             GHUIUtil.getRepositoryDisplayName(allRepos,
                                                                               dataContext.repositoryDataService.repositoryCoordinates))
      currentDisposable?.let { Disposer.dispose(it) }
      currentPullRequest = null
      wrapper.setContent(listComponent)
      wrapper.repaint()
      if (requestFocus) GHUIUtil.focusPanel(wrapper.targetComponent)
    }

    override fun refreshList() {
      dataContext.listLoader.reset()
      dataContext.repositoryDataService.resetData()
    }

    override fun viewPullRequest(id: GHPRIdentifier, requestFocus: Boolean, onShown: ((GHPRViewComponentController?) -> Unit)?) {
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
      if (onShown != null) onShown(UIUtil.getClientProperty(wrapper.targetComponent, GHPRViewComponentController.KEY))
      if (requestFocus) GHUIUtil.focusPanel(wrapper.targetComponent)
    }

    override fun openPullRequestTimeline(id: GHPRIdentifier, requestFocus: Boolean) =
      dataContext.filesManager.createAndOpenTimelineFile(id, requestFocus)

    override fun openPullRequestDiff(id: GHPRIdentifier, requestFocus: Boolean) =
      dataContext.filesManager.createAndOpenDiffFile(id, requestFocus)

    override fun openNewPullRequestDiff(requestFocus: Boolean) {
      dataContext.filesManager.openNewPRDiffFile(requestFocus)
    }
  }
}