// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.auth.AccountsListener
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.content.Content
import com.intellij.util.IJSwingUtilities
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingPanelFactory
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateComponentHolder
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHProjectRepositoriesManager
import java.awt.BorderLayout
import javax.swing.JComponent
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
  private val tabDisposable = Disposer.newCheckedDisposable().also {
    Disposer.register(tab.disposer!!, it)
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
    authManager.addListener(tabDisposable, object : AccountsListener<GithubAccount> {
      override fun onAccountListChanged(old: Collection<GithubAccount>, new: Collection<GithubAccount>) = scheduleUpdate()
      override fun onAccountCredentialsChanged(account: GithubAccount) = scheduleUpdate()

      private fun scheduleUpdate() = ApplicationManager.getApplication()
        .invokeLater(Runnable { Updater().update() }) { tabDisposable.isDisposed }
    })
    repositoryManager.addRepositoryListChangedListener(tabDisposable) {
      Updater().update()
    }
    Updater().update()
  }

  private inner class Updater {
    private val repos = repositoryManager.knownRepositories
    private val accounts = authManager.getAccounts()

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
        val matchingAccounts = accounts.filter { it.server.equals(repo.ghRepositoryCoordinates.serverPath, true) }
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
      GHUIUtil.focusPanel(mainPanel)
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

    val repository = repositoryMapping.ghRepositoryCoordinates
    val remote = repositoryMapping.gitRemoteUrlCoordinates

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

    val matchingAccounts = authManager.getAccounts().filter { it.server.equals(singleRepo.ghRepositoryCoordinates.serverPath, true) }
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

    private val listComponent by lazy { createListPanel() }
    private val createComponentHolder = ClearableLazyValue.create {
      GHPRCreateComponentHolder(ActionManager.getInstance(), project, projectSettings, repositoryManager, dataContext, this,
                                parentDisposable)
    }

    override lateinit var currentView: GHPRToolWindowViewType
    private var currentDisposable: Disposable? = null
    private var currentPullRequest: GHPRIdentifier? = null

    init {
      when (initialView) {
        GHPRToolWindowViewType.NEW -> createPullRequest(false)
        else -> viewList(false)
      }

      DataManager.registerDataProvider(wrapper) { dataId ->
        when {
          GHPRActionKeys.PULL_REQUESTS_TAB_CONTROLLER.`is`(dataId) -> this
          else -> null
        }
      }
    }

    override fun createPullRequest(requestFocus: Boolean) {
      val allRepos = repositoryManager.knownRepositories.map(GHGitRepositoryMapping::ghRepositoryCoordinates)
      tab.displayName = GithubBundle.message("tab.title.pull.requests.new",
                                             GHUIUtil.getRepositoryDisplayName(allRepos,
                                                                               dataContext.repositoryDataService.repositoryCoordinates))
      currentDisposable?.let { Disposer.dispose(it) }
      currentPullRequest = null
      currentView = GHPRToolWindowViewType.NEW
      wrapper.setContent(createComponentHolder.value.component)
      IJSwingUtilities.updateComponentTreeUI(wrapper)
      if (requestFocus) GHUIUtil.focusPanel(wrapper.targetComponent)
    }

    override fun resetNewPullRequestView() {
      createComponentHolder.value.resetModel()
    }

    override fun viewList(requestFocus: Boolean) {
      val allRepos = repositoryManager.knownRepositories.map(GHGitRepositoryMapping::ghRepositoryCoordinates)
      tab.displayName = GithubBundle.message("tab.title.pull.requests.at",
                                             GHUIUtil.getRepositoryDisplayName(allRepos,
                                                                               dataContext.repositoryDataService.repositoryCoordinates))
      currentDisposable?.let { Disposer.dispose(it) }
      currentPullRequest = null
      currentView = GHPRToolWindowViewType.LIST
      wrapper.setContent(listComponent)
      IJSwingUtilities.updateComponentTreeUI(wrapper)
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
        currentView = GHPRToolWindowViewType.DETAILS
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

    private fun createListPanel(): JComponent {
      val listLoader = dataContext.listLoader
      val listModel = CollectionListModel(listLoader.loadedData)
      listLoader.addDataListener(parentDisposable, object : GHListLoader.ListDataListener {
        override fun onDataAdded(startIdx: Int) {
          val loadedData = listLoader.loadedData
          listModel.add(loadedData.subList(startIdx, loadedData.size))
        }

        override fun onDataUpdated(idx: Int) = listModel.setElementAt(listLoader.loadedData[idx], idx)
        override fun onDataRemoved(data: Any) {
          (data as? GHPullRequestShort)?.let { listModel.remove(it) }
        }

        override fun onAllDataRemoved() = listModel.removeAll()
      })

      val list = GHPRListComponentFactory(listModel).create(dataContext.avatarIconsProvider)

      return GHPRListPanelFactory(project,
                                  dataContext.repositoryDataService,
                                  dataContext.securityService,
                                  dataContext.listLoader,
                                  dataContext.listUpdatesChecker,
                                  dataContext.securityService.account,
                                  parentDisposable)
        .create(list, dataContext.avatarIconsProvider)
    }
  }
}
