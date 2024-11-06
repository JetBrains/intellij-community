// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.collaboration.auth.ui.CompactAccountsPanelFactory
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.util.LinkActionMouseAdapter
import com.intellij.collaboration.util.CollectionDelta
import com.intellij.dvcs.repo.ClonePathProvider
import com.intellij.dvcs.ui.CloneDvcsValidationUtils
import com.intellij.dvcs.ui.DvcsBundle.message
import com.intellij.dvcs.ui.FilePathDocumentChildPathHandle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.cloneDialog.AccountMenuItem
import com.intellij.util.ui.cloneDialog.VcsCloneDialogUiSpec
import git4idea.GitUtil
import git4idea.checkout.GitCheckoutProvider
import git4idea.commands.Git
import git4idea.remote.GitRememberedInputs
import git4idea.ui.GitShallowCloneComponentFactory
import git4idea.ui.GitShallowCloneViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GHAccountsDetailsProvider
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.GithubGitHelper
import org.jetbrains.plugins.github.util.GithubNotificationIdsHolder
import org.jetbrains.plugins.github.util.GithubNotifications
import org.jetbrains.plugins.github.util.GithubUrlUtil
import java.awt.event.ActionEvent
import java.nio.file.Paths
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JSeparator
import javax.swing.ListModel
import javax.swing.event.DocumentEvent
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import kotlin.properties.Delegates

private val LOG = logger<GHCloneDialogExtensionComponentBase>()

internal abstract class GHCloneDialogExtensionComponentBase(
  private val project: Project,
  parentCs: CoroutineScope,
  private val accountManager: GHAccountManager
) : VcsCloneDialogExtensionComponent() {

  private val githubGitHelper: GithubGitHelper = GithubGitHelper.getInstance()

  private val cs = parentCs.childScope(javaClass.name, Dispatchers.Main)

  // UI
  private val wrapper: Wrapper = Wrapper()
  private val repositoriesPanel: DialogPanel
  private val repositoryList: JBList<GHRepositoryListItem>

  private val searchField: SearchTextField
  private val directoryField = TextFieldWithBrowseButton().apply {
    addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withShowFileSystemRoots(true)
      .withHideIgnored(false)
      .withTitle(message("clone.destination.directory.browser.title"))
      .withDescription(message("clone.destination.directory.browser.description")))
  }
  private val cloneDirectoryChildHandle = FilePathDocumentChildPathHandle
    .install(directoryField.textField.document, ClonePathProvider.defaultParentDirectoryPath(project, GitRememberedInputs.getInstance()))

  // state
  private val loader = GHCloneDialogRepositoryListLoaderImpl(cs)
  private var inLoginState = false
  private var selectedUrl by Delegates.observable<String?>(null) { _, _, _ -> onSelectedUrlChanged() }

  protected val content: JComponent get() = wrapper.targetComponent

  private val accountListModel: ListModel<GithubAccount> = createAccountsModel()

  private val shallowCloneModel = GitShallowCloneViewModel()

  init {
    repositoryList = JBList(loader.listModel).apply {
      cellRenderer = GHRepositoryListCellRenderer(ErrorHandler()) { accountListModel.itemsSet }
      isFocusable = false
      selectionModel = loader.listSelectionModel
    }.also {
      val mouseAdapter = LinkActionMouseAdapter(it)
      it.addMouseListener(mouseAdapter)
      it.addMouseMotionListener(mouseAdapter)
      it.addListSelectionListener { evt ->
        if (evt.valueIsAdjusting) return@addListSelectionListener
        updateSelectedUrl()
      }
    }
    //TODO: fix jumping selection in the presence of filter
    loader.addLoadingStateListener {
      repositoryList.setPaintBusy(loader.loading)
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

    val accountDetailsProvider = GHAccountsDetailsProvider(cs, accountManager)

    val accountsPanel = CompactAccountsPanelFactory(accountListModel)
      .create(accountDetailsProvider, VcsCloneDialogUiSpec.Components.avatarSize, AccountsPopupConfig())

    repositoriesPanel = panel {
      row {
        cell(searchField.textEditor)
          .resizableColumn()
          .align(Align.FILL)
        cell(JSeparator(JSeparator.VERTICAL))
          .align(AlignY.FILL)
        cell(accountsPanel)
          .align(AlignY.FILL)
      }
      row {
        scrollCell(repositoryList)
          .resizableColumn()
          .align(Align.FILL)
      }.resizableRow()
      row(CollaborationToolsBundle.message("clone.dialog.directory.to.clone.label.text")) {
        cell(directoryField)
          .align(AlignX.FILL)
          .validationOnApply {
            CloneDvcsValidationUtils.checkDirectory(it.text, it.textField)
          }
      }
      GitShallowCloneComponentFactory.appendShallowCloneRow(this, shallowCloneModel)
    }
    repositoriesPanel.border = JBEmptyBorder(UIUtil.getRegularPanelInsets())
    setupAccountsListeners()
  }

  private inner class ErrorHandler : GHRepositoryListCellRenderer.ErrorHandler {

    override fun getPresentableText(error: Throwable): @Nls String = when (error) {
      is GithubMissingTokenException -> CollaborationToolsBundle.message("account.token.missing")
      is GithubAuthenticationException -> GithubBundle.message("credentials.invalid.auth.data", "")
      else -> CollaborationToolsBundle.message("clone.dialog.error.load.repositories")
    }

    override fun getAction(account: GithubAccount, error: Throwable) = when (error) {
      is GithubAuthenticationException -> object : AbstractAction(GithubBundle.message("accounts.relogin")) {
        override fun actionPerformed(e: ActionEvent?) {
          switchToLogin(account)
        }
      }
      else -> object : AbstractAction(GithubBundle.message("retry.link")) {
        override fun actionPerformed(e: ActionEvent?) {
          loader.clear(account)
          loader.loadRepositories(account)
        }
      }
    }
  }

  protected abstract fun isAccountHandled(account: GithubAccount): Boolean

  protected fun getAccounts(): Set<GithubAccount> = accountListModel.itemsSet

  protected abstract fun createLoginPanel(cs: CoroutineScope,
                                          account: GithubAccount?,
                                          cancelHandler: () -> Unit): JComponent

  private fun setupAccountsListeners() {
    accountListModel.addListDataListener(object : ListDataListener {

      private var currentList by Delegates.observable(emptySet<GithubAccount>()) { _, oldValue, newValue ->
        val delta = CollectionDelta(oldValue, newValue)
        for (account in delta.removedItems) {
          loader.clear(account)
        }
        for (account in delta.newItems) {
          loader.loadRepositories(account)
        }

        if (newValue.isEmpty()) {
          switchToLogin(null)
        }
        else {
          switchToRepositories()
        }
        dialogStateListener.onListItemChanged()
      }

      init {
        currentList = accountListModel.itemsSet
      }

      override fun intervalAdded(e: ListDataEvent) {
        currentList = accountListModel.itemsSet
      }

      override fun intervalRemoved(e: ListDataEvent) {
        currentList = accountListModel.itemsSet
      }

      override fun contentsChanged(e: ListDataEvent) {
        for (i in e.index0..e.index1) {
          val account = accountListModel.getElementAt(i)
          loader.clear(account)
          loader.loadRepositories(account)
        }
        switchToRepositories()
        dialogStateListener.onListItemChanged()
      }
    })
  }

  protected fun switchToLogin(account: GithubAccount?) {
    wrapper.setContent(createLoginPanel(cs, account) { switchToRepositories() })
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

  override fun getView() = wrapper

  override fun doValidateAll(): List<ValidationInfo> =
    (wrapper.targetComponent as? DialogPanel)?.validationsOnApply?.values?.flatten()?.mapNotNull {
      it.validate()
    } ?: emptyList()

  override fun doClone(checkoutListener: CheckoutProvider.Listener) {
    val parent = Paths.get(directoryField.text).toAbsolutePath().parent
    val destinationValidation = CloneDvcsValidationUtils.createDestination(parent.toString())
    if (destinationValidation != null) {
      LOG.error(CollaborationToolsBundle.message("clone.dialog.error.unable.to.create.destination.directory"),
                destinationValidation.message)
      GithubNotifications.showError(project,
                                    GithubNotificationIdsHolder.CLONE_UNABLE_TO_CREATE_DESTINATION_DIR,
                                    CollaborationToolsBundle.message("clone.dialog.clone.failed"),
                                    CollaborationToolsBundle.message("clone.dialog.error.unable.to.find.destination.directory"))
      return
    }

    val lfs = LocalFileSystem.getInstance()
    var destinationParent = lfs.findFileByIoFile(parent.toFile())
    if (destinationParent == null) {
      destinationParent = lfs.refreshAndFindFileByIoFile(parent.toFile())
    }
    if (destinationParent == null) {
      LOG.error(CollaborationToolsBundle.message("clone.dialog.error.destination.not.exist"))
      GithubNotifications.showError(project,
                                    GithubNotificationIdsHolder.CLONE_UNABLE_TO_FIND_DESTINATION,
                                    CollaborationToolsBundle.message("clone.dialog.clone.failed"),
                                    CollaborationToolsBundle.message("clone.dialog.error.unable.to.find.destination.directory"))
      return
    }
    val directoryName = Paths.get(directoryField.text).fileName.toString()
    val parentDirectory = parent.toAbsolutePath().toString()

    GitCheckoutProvider.clone(
      project,
      Git.getInstance(),
      checkoutListener,
      destinationParent,
      selectedUrl,
      directoryName,
      parentDirectory,
      shallowCloneModel.getShallowCloneOptions(),
    )
  }

  override fun onComponentSelected() {
    dialogStateListener.onOkActionNameChanged(message("clone.button"))
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
      repositoryList.emptyText.appendText(CollaborationToolsBundle.message("clone.dialog.repository.url.text", selectedUrl!!))
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
    val model = CollectionListModel<GithubAccount>()
    cs.launch(Dispatchers.Main.immediate) {
      accountManager.accountsState
        .map { it.filter(::isAccountHandled).toSet() }
        .collectLatest { accounts ->
          val currentAccounts = model.items
          accounts.forEach {
            if (!currentAccounts.contains(it)) {
              model.add(it)
              async {
                accountManager.getCredentialsFlow(it).collect { _ ->
                  model.contentsChanged(it)
                }
              }
            }
          }

          currentAccounts.forEach {
            if (!accounts.contains(it)) {
              model.remove(it)
            }
          }
        }
    }
    return model
  }

  private inner class AccountsPopupConfig : CompactAccountsPanelFactory.PopupConfig<GithubAccount> {
    override val avatarSize: Int = VcsCloneDialogUiSpec.Components.popupMenuAvatarSize

    override fun createActions(): Collection<AccountMenuItem.Action> = createAccountMenuLoginActions(null)
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

  companion object {
    internal val <E> ListModel<E>.items
      get() = Iterable {
        object : Iterator<E> {
          private var idx = -1

          override fun hasNext(): Boolean = idx < size - 1

          override fun next(): E {
            idx++
            return getElementAt(idx)
          }
        }
      }

    internal val <E> ListModel<E>.itemsSet
      get() = items.toSet()
  }
}
