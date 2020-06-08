// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.dvcs.repo.ClonePathProvider
import com.intellij.dvcs.ui.CloneDvcsValidationUtils
import com.intellij.dvcs.ui.DvcsBundle.getString
import com.intellij.dvcs.ui.SelectChildTextFieldWithBrowseButton
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
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
import com.intellij.ui.layout.*
import com.intellij.util.IconUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.progress.ProgressVisibilityManager
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.cloneDialog.*
import com.intellij.util.ui.cloneDialog.AccountMenuItem.Account
import com.intellij.util.ui.cloneDialog.AccountMenuItem.Action
import git4idea.GitUtil
import git4idea.checkout.GitCheckoutProvider
import git4idea.commands.Git
import git4idea.remote.GitRememberedInputs
import icons.GithubIcons
import org.jetbrains.plugins.github.api.*
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.api.data.GithubRepo
import org.jetbrains.plugins.github.api.data.request.Affiliation
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.*
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.util.*
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Paths
import javax.swing.*
import javax.swing.event.DocumentEvent
import kotlin.properties.Delegates

internal abstract class BaseCloneDialogExtensionComponent(
  private val project: Project,
  private val authenticationManager: GithubAuthenticationManager,
  private val executorManager: GithubApiRequestExecutorManager,
  private val accountInformationProvider: GithubAccountInformationProvider,
  private val avatarLoader: CachingGithubUserAvatarLoader,
  private val imageResizer: GithubImageResizer
) : VcsCloneDialogExtensionComponent() {
  private val LOG = GithubUtil.LOG

  private val progressManager: ProgressVisibilityManager
  private val githubGitHelper: GithubGitHelper = GithubGitHelper.getInstance()

  // UI
  private val defaultAvatar = resizeIcon(GithubIcons.DefaultAvatar, VcsCloneDialogUiSpec.Components.avatarSize)
  private val defaultPopupAvatar = resizeIcon(GithubIcons.DefaultAvatar, VcsCloneDialogUiSpec.Components.popupMenuAvatarSize)
  private val avatarSizeUiInt = JBValue.UIInteger("GHCloneDialogExtensionComponent.popupAvatarSize",
                                                  VcsCloneDialogUiSpec.Components.popupMenuAvatarSize)


  private val wrapper: Wrapper = Wrapper()
  private val repositoriesPanel: DialogPanel
  private val repositoryList: JBList<GHRepositoryListItem>

  private val popupMenuMouseAdapter = object : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent?) = showPopupMenu()
  }

  private val accountsPanel: JPanel = JPanel(FlowLayout(FlowLayout.LEADING, JBUI.scale(1), 0)).apply {
    addMouseListener(popupMenuMouseAdapter)
  }

  private val searchField: SearchTextField
  private val directoryField = SelectChildTextFieldWithBrowseButton(
    ClonePathProvider.defaultParentDirectoryPath(project, GitRememberedInputs.getInstance())).apply {
    val fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor()
    fcd.isShowFileSystemRoots = true
    fcd.isHideIgnored = false
    addBrowseFolderListener(getString("clone.destination.directory.browser.title"),
                            getString("clone.destination.directory.browser.description"),
                            project,
                            fcd)
  }

  // state
  private val userDetailsByAccount = hashMapOf<GithubAccount, GithubAuthenticatedUser>()
  private val repositoriesByAccount = hashMapOf<GithubAccount, LinkedHashSet<GithubRepo>>()
  private val errorsByAccount = hashMapOf<GithubAccount, GHRepositoryListItem.Error>()
  private val originListModel = CollectionListModel<GHRepositoryListItem>()
  private var inLoginState = false
  private var selectedUrl by Delegates.observable<String?>(null) { _, _, _ -> onSelectedUrlChanged() }

  // popup menu
  private val accountComponents = hashMapOf<GithubAccount, JLabel>()
  private val avatarsByAccount = hashMapOf<GithubAccount, Icon>()

  init {
    val listWithSearchBundle = ListWithSearchComponent(originListModel, GHRepositoryListCellRenderer { getAccounts() })

    repositoryList = listWithSearchBundle.list
    val mouseAdapter = GHRepositoryMouseAdapter(repositoryList)
    repositoryList.addMouseListener(mouseAdapter)
    repositoryList.addMouseMotionListener(mouseAdapter)
    repositoryList.addListSelectionListener {
      if (it.valueIsAdjusting) return@addListSelectionListener
      updateSelectedUrl()
    }

    searchField = listWithSearchBundle.searchField
    searchField.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) = updateSelectedUrl()
    })
    createFocusFilterFieldAction(searchField)

    progressManager = object : ProgressVisibilityManager() {
      override fun setProgressVisible(visible: Boolean) = repositoryList.setPaintBusy(visible)

      override fun getModalityState() = ModalityState.any()
    }

    Disposer.register(this, progressManager)

    ApplicationManager.getApplication().messageBus.connect(this).apply {
      subscribe(GithubAccountManager.ACCOUNT_REMOVED_TOPIC, object : AccountRemovedListener {
        override fun accountRemoved(removedAccount: GithubAccount) {
          removeAccount(removedAccount)
          dialogStateListener.onListItemChanged()
        }
      })

      subscribe(GithubAccountManager.ACCOUNT_TOKEN_CHANGED_TOPIC, object : AccountTokenChangedListener {
        override fun tokenChanged(account: GithubAccount) {
          if (repositoriesByAccount[account] != null)
            return
          dialogStateListener.onListItemChanged()
          addAccount(account)
          switchToRepositories()
        }
      })
    }

    repositoriesPanel = panel {
      row {
        cell(isFullWidth = true) {
          searchField.textEditor(pushX, growX)
          JSeparator(JSeparator.VERTICAL)(growY).withLargeLeftGap()
          accountsPanel().withLargeLeftGap()
        }
      }
      row {
        ScrollPaneFactory.createScrollPane(repositoryList)(push, grow)
      }
      row(GithubBundle.message("clone.dialog.directory.field")) {
        directoryField(growX, pushX)
      }
    }
    repositoriesPanel.border = JBEmptyBorder(UIUtil.getRegularPanelInsets())
  }

  protected abstract fun getAccounts(): Collection<GithubAccount>

  fun setup() {
    val accounts = getAccounts()
    if (accounts.isNotEmpty()) {
      switchToRepositories()
      accounts.forEach(::addAccount)
    }
    else {
      switchToLogin()
    }
  }

  private fun switchToLogin(account: GithubAccount? = null) {
    val loginPanel = GHCloneDialogLoginPanel(account).apply {
      isCancelVisible = getAccounts().isNotEmpty()
      setCancelHandler { switchToRepositories() }
    }

    wrapper.setContent(loginPanel)
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

  private fun addAccount(account: GithubAccount) {
    repositoriesByAccount.remove(account)

    val label = accountComponents.getOrPut(account) {
      JLabel().apply {
        icon = defaultAvatar
        toolTipText = account.name
        isOpaque = false
        addMouseListener(popupMenuMouseAdapter)
      }
    }
    accountsPanel.add(label)

    try {
      val executor = executorManager.getExecutor(account)
      loadUserDetails(account, executor)
      loadRepositories(account, executor)
    }
    catch (e: GithubMissingTokenException) {
      errorsByAccount[account] = GHRepositoryListItem.Error(account,
                                                            GithubBundle.message("account.token.missing"),
                                                            GithubBundle.message("login.link"),
                                                            Runnable { switchToLogin(account) })
      refillRepositories()
    }
  }

  private fun removeAccount(account: GithubAccount) {
    repositoriesByAccount.remove(account)
    accountComponents.remove(account).let {
      accountsPanel.remove(it)
      accountsPanel.revalidate()
      accountsPanel.repaint()
    }
    refillRepositories()
    if (getAccounts().isEmpty()) switchToLogin()
  }

  private fun loadUserDetails(account: GithubAccount,
                              executor: GithubApiRequestExecutor.WithTokenAuth) {
    progressManager.run(object : Task.Backgroundable(project, "Not Visible") {
      lateinit var user: GithubAuthenticatedUser
      lateinit var iconProvider: CachingGithubAvatarIconsProvider

      override fun run(indicator: ProgressIndicator) {
        user = accountInformationProvider.getInformation(executor, indicator, account)
        iconProvider = CachingGithubAvatarIconsProvider
          .Factory(avatarLoader, imageResizer, executor)
          .create(avatarSizeUiInt, accountsPanel)
      }

      override fun onSuccess() {
        userDetailsByAccount[account] = user
        val avatar = iconProvider.getIcon(user.avatarUrl)
        avatarsByAccount[account] = avatar
        accountComponents[account]?.icon = resizeIcon(avatar, VcsCloneDialogUiSpec.Components.avatarSize)
        refillRepositories()
      }

      override fun onThrowable(error: Throwable) {
        LOG.error(error)
        errorsByAccount[account] = GHRepositoryListItem.Error(account,
                                                              GithubBundle.message("clone.error.load.repositories"),
                                                              GithubBundle.message("retry.link"),
                                                              Runnable { addAccount(account) })
      }
    })
  }

  private fun loadRepositories(account: GithubAccount,
                               executor: GithubApiRequestExecutor.WithTokenAuth) {
    repositoriesByAccount.remove(account)
    errorsByAccount.remove(account)

    progressManager.run(object : Task.Backgroundable(project, "Not Visible") {
      override fun run(indicator: ProgressIndicator) {
        val repoPagesRequest = GithubApiRequests.CurrentUser.Repos.pages(account.server,
                                                                         affiliation = Affiliation.combine(Affiliation.OWNER,
                                                                                                           Affiliation.COLLABORATOR),
                                                                         pagination = GithubRequestPagination.DEFAULT)
        val pageItemsConsumer: (List<GithubRepo>) -> Unit = {
          runInEdt {
            repositoriesByAccount.getOrPut(account, { UpdateOrderLinkedHashSet() }).addAll(it)
            refillRepositories()
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
        LOG.error(error)
        errorsByAccount[account] = GHRepositoryListItem.Error(account,
                                                              GithubBundle.message("clone.error.load.repositories"),
                                                              GithubBundle.message("retry.link"),
                                                              Runnable { loadRepositories(account, executor) })
      }
    })
  }

  private fun refillRepositories() {
    val selectedValue = repositoryList.selectedValue
    originListModel.removeAll()
    for (account in getAccounts()) {
      if (errorsByAccount[account] != null) {
        originListModel.add(errorsByAccount[account])
      }
      val user = userDetailsByAccount[account] ?: continue
      val repos = repositoriesByAccount[account] ?: continue
      for (repo in repos) {
        originListModel.add(GHRepositoryListItem.Repo(account, user, repo))
      }
    }
    repositoryList.setSelectedValue(selectedValue, false)
    ScrollingUtil.ensureSelectionExists(repositoryList)
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
      GithubNotifications.showError(project, GithubBundle.message("clone.dialog.clone.failed"),
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
      GithubNotifications.showError(project, GithubBundle.message("clone.dialog.clone.failed"),
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
      directoryField.trySetChildPath(path)
    }
  }

  /**
   * Since each repository can be in several states at the same time (shared access for a collaborator and shared access for org member) and
   * repositories for collaborators are loaded in separate request before repositories for org members, we need to update order of re-added
   * repo in order to place it close to other organization repos
   */
  private class UpdateOrderLinkedHashSet<T> : LinkedHashSet<T>() {
    override fun add(element: T): Boolean {
      val wasThere = remove(element)
      super.add(element)
      // Contract is "true if this set did not already contain the specified element"
      return !wasThere
    }
  }

  private fun resizeIcon(icon: Icon, size: Int): Icon {
    val scale = JBUI.scale(size).toFloat() / icon.iconWidth.toFloat()
    return IconUtil.scale(icon, null, scale)
  }

  private fun showPopupMenu() {
    val menuItems = mutableListOf<AccountMenuItem>()
    val project = ProjectManager.getInstance().defaultProject

    for ((index, account) in getAccounts().withIndex()) {
      val user = userDetailsByAccount[account]

      val accountTitle = user?.login ?: account.name
      val serverInfo = account.server.toUrl().removePrefix("http://").removePrefix("https://")
      val avatar = avatarsByAccount[account] ?: defaultPopupAvatar
      val accountActions = mutableListOf<Action>()
      val showSeparatorAbove = index != 0

      if (user == null) {
        accountActions += Action(GithubBundle.message("login.action"), { switchToLogin(account) })
        accountActions += Action(GithubBundle.message("accounts.remove"), { authenticationManager.removeAccount(account) },
                                 showSeparatorAbove = true)
      }
      else {
        if (account != authenticationManager.getDefaultAccount(project)) {
          accountActions += Action(GithubBundle.message("accounts.set.default"),
                                   { authenticationManager.setDefaultAccount(project, account) })
        }
        accountActions += Action(GithubBundle.message("open.on.github.action"), { BrowserUtil.browse(user.htmlUrl) },
                                 AllIcons.Ide.External_link_arrow)
        accountActions += Action(GithubBundle.message("accounts.log.out"), { authenticationManager.removeAccount(account) },
                                 showSeparatorAbove = true)
      }

      menuItems += Account(accountTitle, serverInfo, avatar, accountActions, showSeparatorAbove)
    }
    menuItems += Action(GithubBundle.message("accounts.add"), { switchToLogin() }, showSeparatorAbove = true)

    AccountsMenuListPopup(null, AccountMenuPopupStep(menuItems)).showUnderneathOf(accountsPanel)
  }

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
