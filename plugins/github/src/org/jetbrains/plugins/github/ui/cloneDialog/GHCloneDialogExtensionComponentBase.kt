// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.collaboration.auth.AccountsListener
import com.intellij.collaboration.auth.ui.CompactAccountsPanelFactory
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.util.ProgressIndicatorsProvider
import com.intellij.dvcs.repo.ClonePathProvider
import com.intellij.dvcs.ui.CloneDvcsValidationUtils
import com.intellij.dvcs.ui.DvcsBundle.message
import com.intellij.dvcs.ui.FilePathDocumentChildPathHandle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.progress.ProgressVisibilityManager
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.cloneDialog.AccountMenuItem
import com.intellij.util.ui.cloneDialog.VcsCloneDialogUiSpec
import git4idea.GitUtil
import git4idea.checkout.GitCheckoutProvider
import git4idea.commands.Git
import git4idea.remote.GitRememberedInputs
import org.jetbrains.plugins.github.GithubIcons
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubRepo
import org.jetbrains.plugins.github.api.data.request.Affiliation
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GHAccountsDetailsLoader
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.*
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JSeparator
import javax.swing.ListModel
import javax.swing.event.DocumentEvent
import kotlin.properties.Delegates

internal abstract class GHCloneDialogExtensionComponentBase(
  private val project: Project,
  private val authenticationManager: GithubAuthenticationManager,
  private val executorManager: GithubApiRequestExecutorManager
) : VcsCloneDialogExtensionComponent() {

  private val LOG = GithubUtil.LOG

  private val progressManager: ProgressVisibilityManager
  private val githubGitHelper: GithubGitHelper = GithubGitHelper.getInstance()

  // UI
  private val wrapper: Wrapper = Wrapper()
  private val repositoriesPanel: DialogPanel
  private val repositoryList: JBList<GHRepositoryListItem>

  private val searchField: SearchTextField
  private val directoryField = TextFieldWithBrowseButton().apply {
    val fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor()
    fcd.isShowFileSystemRoots = true
    fcd.isHideIgnored = false
    addBrowseFolderListener(message("clone.destination.directory.browser.title"),
                            message("clone.destination.directory.browser.description"),
                            project,
                            fcd)
  }
  private val cloneDirectoryChildHandle = FilePathDocumentChildPathHandle
    .install(directoryField.textField.document, ClonePathProvider.defaultParentDirectoryPath(project, GitRememberedInputs.getInstance()))

  // state
  private val repositoryListModel = GHCloneDialogRepositoryListModel()
  private var inLoginState = false
  private var selectedUrl by Delegates.observable<String?>(null) { _, _, _ -> onSelectedUrlChanged() }

  protected val content: JComponent get() = wrapper.targetComponent

  init {
    repositoryList = JBList(repositoryListModel).apply {
      cellRenderer = GHRepositoryListCellRenderer { getAccounts() }
      isFocusable = false
      selectionModel = SingleSelectionModel()
    }.also {
      val mouseAdapter = GHRepositoryMouseAdapter(it)
      it.addMouseListener(mouseAdapter)
      it.addMouseMotionListener(mouseAdapter)
      it.addListSelectionListener { evt ->
        if (evt.valueIsAdjusting) return@addListSelectionListener
        updateSelectedUrl()
      }
    }

    searchField = SearchTextField(false).also {
      it.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) = updateSelectedUrl()
      })
      createFocusFilterFieldAction(it)
    }

    CollaborationToolsUIUtil.attachSearch(repositoryList, searchField) {
      when (it) {
        is GHRepositoryListItem.Repo -> it.repo.fullName
        is GHRepositoryListItem.Error -> ""
      }
    }

    progressManager = object : ProgressVisibilityManager() {
      override fun setProgressVisible(visible: Boolean) = repositoryList.setPaintBusy(visible)

      override fun getModalityState() = ModalityState.any()
    }

    val indicatorsProvider = ProgressIndicatorsProvider()

    @Suppress("LeakingThis")
    val parentDisposable: Disposable = this
    Disposer.register(parentDisposable, progressManager)
    Disposer.register(parentDisposable, indicatorsProvider)


    val accountDetailsLoader = GHAccountsDetailsLoader(indicatorsProvider) {
      try {
        executorManager.getExecutor(it)
      }
      catch (e: Exception) {
        null
      }
    }

    val accountsPanel = CompactAccountsPanelFactory(createAccountsModel(), accountDetailsLoader)
      .create(GithubIcons.DefaultAvatar, VcsCloneDialogUiSpec.Components.avatarSize, AccountsPopupConfig())

    repositoriesPanel = panel {
      row {
        cell(searchField.textEditor)
          .resizableColumn()
          .verticalAlign(VerticalAlign.FILL)
          .horizontalAlign(HorizontalAlign.FILL)
        cell(JSeparator(JSeparator.VERTICAL))
          .verticalAlign(VerticalAlign.FILL)
        cell(accountsPanel)
          .verticalAlign(VerticalAlign.FILL)
      }
      row {
        scrollCell(repositoryList)
          .resizableColumn()
          .verticalAlign(VerticalAlign.FILL)
          .horizontalAlign(HorizontalAlign.FILL)
      }.resizableRow()
      row(GithubBundle.message("clone.dialog.directory.field")) {
        cell(directoryField)
          .horizontalAlign(HorizontalAlign.FILL)
      }
    }
    repositoriesPanel.border = JBEmptyBorder(UIUtil.getRegularPanelInsets())
    setupAccountsListeners()
  }

  protected abstract fun isAccountHandled(account: GithubAccount): Boolean

  protected fun getAccounts(): List<GithubAccount> = authenticationManager.getAccounts().filter(::isAccountHandled)

  protected abstract fun createLoginPanel(account: GithubAccount?, cancelHandler: () -> Unit): JComponent

  private fun setupAccountsListeners() {
    authenticationManager.addListener(this, object : AccountsListener<GithubAccount> {
      override fun onAccountListChanged(old: Collection<GithubAccount>, new: Collection<GithubAccount>) {
        val oldList = old.filter(::isAccountHandled)
        val newList = new.filter(::isAccountHandled)
        val delta = CollectionDelta(oldList, newList)
        for (account in delta.removedItems) {
          repositoryListModel.clear(account)
        }
        for (account in delta.newItems) {
          loadRepositories(account)
        }
        if (delta.newItems.isEmpty()) {
          switchToLogin(null)
        }
        else {
          switchToRepositories()
        }
        dialogStateListener.onListItemChanged()
      }

      override fun onAccountCredentialsChanged(account: GithubAccount) {
        if (!isAccountHandled(account)) return
        loadRepositories(account)
        switchToRepositories()
        dialogStateListener.onListItemChanged()
      }
    })
    val accounts = getAccounts()
    if (accounts.isNotEmpty()) {
      switchToRepositories()
      for (account in accounts) {
        loadRepositories(account)
      }
    }
    else {
      switchToLogin(null)
    }
  }

  protected fun switchToLogin(account: GithubAccount?) {
    wrapper.setContent(createLoginPanel(account) { switchToRepositories() })
    wrapper.repaint()
    inLoginState = true
    updateSelectedUrl()
  }

  private fun switchToRepositories() {
    wrapper.setContent(repositoriesPanel)
    wrapper.repaint()
    inLoginState = false
    updateSelectedUrl()
  }

  private fun loadRepositories(account: GithubAccount) {
    repositoryListModel.clear(account)

    val executor = try {
      executorManager.getExecutor(account)
    }
    catch (e: GithubMissingTokenException) {
      repositoryListModel.setError(account,
                                   GithubBundle.message("account.token.missing"),
                                   GithubBundle.message("login.link"),
                                   Runnable { switchToLogin(account) })
      ScrollingUtil.ensureSelectionExists(repositoryList)
      return
    }

    progressManager.run(object : Task.Backgroundable(project, GithubBundle.message("progress.title.not.visible")) {
      override fun run(indicator: ProgressIndicator) {
        val details = executor.execute(indicator, GithubApiRequests.CurrentUser.get(account.server))

        val repoPagesRequest = GithubApiRequests.CurrentUser.Repos.pages(account.server,
                                                                         affiliation = Affiliation.combine(Affiliation.OWNER,
                                                                                                           Affiliation.COLLABORATOR),
                                                                         pagination = GithubRequestPagination.DEFAULT)
        val pageItemsConsumer: (List<GithubRepo>) -> Unit = {
          indicator.checkCanceled()
          runInEdt {
            indicator.checkCanceled()
            repositoryListModel.addRepositories(account, details, it)
            ScrollingUtil.ensureSelectionExists(repositoryList)
          }
        }
        GithubApiPagesLoader.loadAll(executor, indicator, repoPagesRequest, pageItemsConsumer)

        val orgsRequest = GithubApiRequests.CurrentUser.Orgs.pages(account.server)
        val userOrganizations = GithubApiPagesLoader.loadAll(executor, indicator, orgsRequest).sortedBy { it.login }

        for (org in userOrganizations) {
          val orgRepoRequest = GithubApiRequests.Organisations.Repos.pages(account.server, org.login, GithubRequestPagination.DEFAULT)
          GithubApiPagesLoader.loadAll(executor, indicator, orgRepoRequest, pageItemsConsumer)
        }
      }

      override fun onThrowable(error: Throwable) {
        if (error is GithubAuthenticationException) {
          repositoryListModel.setError(account,
                                       GithubBundle.message("credentials.invalid.auth.data", ""),
                                       GithubBundle.message("accounts.relogin"),
                                       Runnable { switchToLogin(account) })
        }
        else {
          repositoryListModel.setError(account,
                                       GithubBundle.message("clone.error.load.repositories"),
                                       GithubBundle.message("retry.link"),
                                       Runnable { loadRepositories(account) })
        }
        ScrollingUtil.ensureSelectionExists(repositoryList)
      }
    })
  }

  override fun getView() = wrapper

  override fun doValidateAll(): List<ValidationInfo> {
    val list = ArrayList<ValidationInfo>()
    ContainerUtil.addIfNotNull(list, CloneDvcsValidationUtils.checkDirectory(directoryField.text, directoryField.textField))
    return list
  }

  override fun doClone(checkoutListener: CheckoutProvider.Listener) {
    val parent = Paths.get(directoryField.text).toAbsolutePath().parent
    val destinationValidation = CloneDvcsValidationUtils.createDestination(parent.toString())
    if (destinationValidation != null) {
      LOG.error("Unable to create destination directory", destinationValidation.message)
      GithubNotifications.showError(project,
                                    GithubNotificationIdsHolder.CLONE_UNABLE_TO_CREATE_DESTINATION_DIR,
                                    GithubBundle.message("clone.dialog.clone.failed"),
                                    GithubBundle.message("clone.error.unable.to.create.dest.dir"))
      return
    }

    val lfs = LocalFileSystem.getInstance()
    var destinationParent = lfs.findFileByIoFile(parent.toFile())
    if (destinationParent == null) {
      destinationParent = lfs.refreshAndFindFileByIoFile(parent.toFile())
    }
    if (destinationParent == null) {
      LOG.error("Clone Failed. Destination doesn't exist")
      GithubNotifications.showError(project,
                                    GithubNotificationIdsHolder.CLONE_UNABLE_TO_FIND_DESTINATION,
                                    GithubBundle.message("clone.dialog.clone.failed"),
                                    GithubBundle.message("clone.error.unable.to.find.dest"))
      return
    }
    val directoryName = Paths.get(directoryField.text).fileName.toString()
    val parentDirectory = parent.toAbsolutePath().toString()

    GitCheckoutProvider.clone(project, Git.getInstance(), checkoutListener, destinationParent, selectedUrl, directoryName, parentDirectory)
  }

  override fun onComponentSelected() {
    dialogStateListener.onOkActionNameChanged(GithubBundle.message("clone.button"))
    updateSelectedUrl()

    val focusManager = IdeFocusManager.getInstance(project)
    getPreferredFocusedComponent()?.let { focusManager.requestFocus(it, true) }
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return searchField
  }

  private fun updateSelectedUrl() {
    repositoryList.emptyText.clear()
    if (inLoginState) {
      selectedUrl = null
      return
    }
    val githubRepoPath = getGithubRepoPath(searchField.text)
    if (githubRepoPath != null) {
      selectedUrl = githubGitHelper.getRemoteUrl(githubRepoPath.serverPath,
                                                 githubRepoPath.repositoryPath.owner,
                                                 githubRepoPath.repositoryPath.repository)
      repositoryList.emptyText.appendText(GithubBundle.message("clone.dialog.text", selectedUrl!!))
      return
    }
    val selectedValue = repositoryList.selectedValue
    if (selectedValue is GHRepositoryListItem.Repo) {
      selectedUrl = githubGitHelper.getRemoteUrl(selectedValue.account.server,
                                                 selectedValue.repo.userName,
                                                 selectedValue.repo.name)
      return
    }
    selectedUrl = null
  }


  private fun getGithubRepoPath(searchText: String): GHRepositoryCoordinates? {
    val url = searchText
      .trim()
      .removePrefix("git clone")
      .removeSuffix(".git")
      .trim()

    try {
      var serverPath = GithubServerPath.from(url)
      serverPath = GithubServerPath.from(serverPath.toUrl().removeSuffix(serverPath.suffix ?: ""))

      val githubFullPath = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(url) ?: return null
      return GHRepositoryCoordinates(serverPath, githubFullPath)
    }
    catch (e: Throwable) {
      return null
    }
  }

  private fun onSelectedUrlChanged() {
    val urlSelected = selectedUrl != null
    dialogStateListener.onOkActionEnabled(urlSelected)
    if (urlSelected) {
      val path = StringUtil.trimEnd(ClonePathProvider.relativeDirectoryPathForVcsUrl(project, selectedUrl!!), GitUtil.DOT_GIT)
      cloneDirectoryChildHandle.trySetChildPath(path)
    }
  }

  private fun createAccountsModel(): ListModel<GithubAccount> {
    val model = CollectionListModel(getAccounts())
    authenticationManager.addListener(this, object : AccountsListener<GithubAccount> {
      override fun onAccountListChanged(old: Collection<GithubAccount>, new: Collection<GithubAccount>) {
        val newList = new.filter(::isAccountHandled)
        model.removeAll()
        model.add(newList)
      }

      override fun onAccountCredentialsChanged(account: GithubAccount) {
        if (!isAccountHandled(account)) return
        model.contentsChanged(account)
      }
    })
    return model
  }

  private inner class AccountsPopupConfig : CompactAccountsPanelFactory.PopupConfig<GithubAccount> {
    override val avatarSize: Int = VcsCloneDialogUiSpec.Components.popupMenuAvatarSize

    override fun createActions(): Collection<AccountMenuItem.Action> = createAccountMenuLoginActions(null)

    override fun createActions(account: GithubAccount, requiresReLogin: Boolean): Collection<AccountMenuItem.Action> {
      val actions = mutableListOf<AccountMenuItem.Action>()

      if (requiresReLogin) {
        actions += createAccountMenuLoginActions(account)
      }
      else if (account != authenticationManager.getDefaultAccount(project)) {
        actions += AccountMenuItem.Action(CollaborationToolsBundle.message("accounts.set.default"),
                                          { authenticationManager.setDefaultAccount(project, account) })
      }

      actions += AccountMenuItem.Action(GithubBundle.message("accounts.log.out"),
                                        { authenticationManager.removeAccount(account) },
                                        showSeparatorAbove = true)


      return actions
    }
  }

  protected abstract fun createAccountMenuLoginActions(account: GithubAccount?): Collection<AccountMenuItem.Action>

  private fun createFocusFilterFieldAction(searchField: SearchTextField) {
    val action = DumbAwareAction.create {
      val focusManager = IdeFocusManager.getInstance(project)
      if (focusManager.getFocusedDescendantFor(repositoriesPanel) != null) {
        focusManager.requestFocus(searchField, true)
      }
    }
    val shortcuts = KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_FIND)
    action.registerCustomShortcutSet(shortcuts, repositoriesPanel, this)
  }
}