// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.dvcs.ui.CloneDvcsValidationUtils
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.childScope
import git4idea.checkout.GitCheckoutProvider
import git4idea.commands.Git
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.GitLabApiImpl
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabAccountsDetailsProvider
import org.jetbrains.plugins.gitlab.ui.clone.GitLabCloneViewModel.SearchModel
import org.jetbrains.plugins.gitlab.ui.clone.GitLabCloneViewModel.UIState
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Paths

internal interface GitLabCloneViewModel {
  val uiState: SharedFlow<UIState>
  val accountsRefreshRequest: SharedFlow<Set<GitLabAccount>>
  val isLoading: Flow<Boolean>

  val items: SharedFlow<List<GitLabCloneListItem>>
  val searchValue: SharedFlow<SearchModel>
  val selectedUrl: SharedFlow<String?>

  val accountDetailsProvider: GitLabAccountsDetailsProvider

  fun selectItem(item: GitLabCloneListItem?)

  fun setSearchValue(text: String)

  fun switchToLoginPanel(account: GitLabAccount?)

  fun switchToRepositoryList()

  fun doClone(checkoutListener: CheckoutProvider.Listener, directoryPath: String)

  fun updateAccount(account: GitLabAccount, credentials: String)

  fun isAccountUnique(serverPath: GitLabServerPath, accountName: String): Boolean

  sealed interface UIState {
    class Login(val account: GitLabAccount?) : UIState
    object Repositories : UIState
  }

  sealed interface SearchModel {
    class Url(val url: String) : SearchModel
    object Text : SearchModel
  }
}

internal class GitLabCloneViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  private val accountManager: GitLabAccountManager
) : GitLabCloneViewModel {
  private val vcsNotifier: VcsNotifier = project.service<VcsNotifier>()

  private val cs: CoroutineScope = parentCs.childScope()
  private val taskLauncher: SingleCoroutineLauncher = SingleCoroutineLauncher(cs)

  private val accounts: Flow<Set<GitLabAccount>> = accountManager.accountsState

  private val _uiState: MutableStateFlow<UIState> = MutableStateFlow(UIState.Login(null))
  override val uiState: SharedFlow<UIState> = _uiState.asSharedFlow()

  private val _accountsRefreshRequest: MutableSharedFlow<Set<GitLabAccount>> = MutableSharedFlow()
  override val accountsRefreshRequest: SharedFlow<Set<GitLabAccount>> = _accountsRefreshRequest.asSharedFlow()

  override val isLoading: Flow<Boolean> = taskLauncher.busy

  private val _selectedItem: MutableStateFlow<GitLabCloneListItem?> = MutableStateFlow(null)

  private val _items: MutableStateFlow<List<GitLabCloneListItem>> = MutableStateFlow(emptyList())
  override val items: SharedFlow<List<GitLabCloneListItem>> = _items.asSharedFlow()

  private val _searchValue: MutableStateFlow<SearchModel> = MutableStateFlow(SearchModel.Text)
  override val searchValue: SharedFlow<SearchModel> = _searchValue.asSharedFlow()

  private val _selectedUrl: StateFlow<String?> = combine(_searchValue, _selectedItem) { searchValue, selectedItem ->
    when {
      searchValue is SearchModel.Url -> searchValue.url
      selectedItem != null && selectedItem is GitLabCloneListItem.Repository -> selectedItem.projectMember.project.httpUrlToRepo
      else -> null
    }
  }.stateIn(cs, SharingStarted.Eagerly, initialValue = null)
  override val selectedUrl: SharedFlow<String?> = _selectedUrl

  override val accountDetailsProvider = GitLabAccountsDetailsProvider(cs) { account ->
    val token = accountManager.findCredentials(account) ?: return@GitLabAccountsDetailsProvider null
    GitLabApiImpl { token }
  }

  init {
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      accounts.collectLatest { accounts ->
        _accountsRefreshRequest.emit(accounts)
        if (accounts.isNotEmpty()) {
          switchToRepositoryList()
        }

        accounts.forEach { account ->
          launch {
            accountManager.getCredentialsFlow(account).collectLatest {
              _accountsRefreshRequest.emit(accounts)
            }
          }
        }
      }
    }

    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      _accountsRefreshRequest.collectLatest { accounts ->
        taskLauncher.launch {
          _items.value = accounts.flatMap { account ->
            collectRepositoriesByAccount(account)
          }
        }
        switchToRepositoryList()
      }
    }
  }

  override fun selectItem(item: GitLabCloneListItem?) {
    _selectedItem.value = item
  }

  override fun setSearchValue(text: String) {
    // TODO: implement ssh "git@"
    _searchValue.value = try {
      URL(text) // Check URL correctness
      SearchModel.Url(text)
    }
    catch (_: MalformedURLException) {
      SearchModel.Text
    }
  }

  override fun switchToLoginPanel(account: GitLabAccount?) {
    _uiState.value = UIState.Login(account)
  }

  override fun switchToRepositoryList() {
    _uiState.value = UIState.Repositories
  }

  override fun doClone(checkoutListener: CheckoutProvider.Listener, directoryPath: String) {
    val selectedUrl = _selectedUrl.value ?: error("Clone button is enabled when repository is not selected")
    val parent = Paths.get(directoryPath).toAbsolutePath().parent
    val destinationValidation = CloneDvcsValidationUtils.createDestination(parent.toString())
    if (destinationValidation != null) {
      notifyCreateDirectoryFailed(destinationValidation.message)
      return
    }

    val lfs = LocalFileSystem.getInstance()
    var destinationParent = lfs.findFileByIoFile(parent.toFile())
    if (destinationParent == null) {
      destinationParent = lfs.refreshAndFindFileByIoFile(parent.toFile())
    }
    if (destinationParent == null) {
      notifyDestinationNotFound()
      return
    }

    val directoryName = Paths.get(directoryPath).fileName.toString()
    val parentDirectory = parent.toAbsolutePath().toString()

    GitCheckoutProvider.clone(project, Git.getInstance(), checkoutListener, destinationParent, selectedUrl, directoryName, parentDirectory)
  }

  override fun updateAccount(account: GitLabAccount, credentials: String) {
    cs.launch {
      accountManager.updateAccount(account, credentials)
    }
  }

  override fun isAccountUnique(serverPath: GitLabServerPath, accountName: String): Boolean {
    return accountManager.isAccountUnique(serverPath, accountName)
  }

  private suspend fun collectRepositoriesByAccount(account: GitLabAccount): List<GitLabCloneListItem> {
    val token = accountManager.findCredentials(account) ?: return listOf(
      GitLabCloneListItem.Error(account, CollaborationToolsBundle.message("account.token.missing"))
    )
    val apiClient = GitLabApiImpl { token }
    val currentUser = apiClient.graphQL.getCurrentUser(account.server) ?: return listOf(
      GitLabCloneListItem.Error(account, CollaborationToolsBundle.message("http.status.error.refresh.token"))
    )
    val accountRepositories = currentUser.projectMemberships.map { projectMember ->
      GitLabCloneListItem.Repository(account, projectMember)
    }

    return accountRepositories.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.presentation() })
  }

  private fun notifyCreateDirectoryFailed(message: String) {
    thisLogger().error(CollaborationToolsBundle.message("clone.dialog.error.unable.to.create.destination.directory"), message)
    vcsNotifier.notifyError(
      CLONE_UNABLE_TO_CREATE_DESTINATION_DIRECTORY,
      CollaborationToolsBundle.message("clone.dialog.clone.failed"),
      CollaborationToolsBundle.message("clone.dialog.error.unable.to.find.destination.directory")
    )
  }

  private fun notifyDestinationNotFound() {
    thisLogger().error(CollaborationToolsBundle.message("clone.dialog.error.destination.not.exist"))
    vcsNotifier.notifyError(
      CLONE_UNABLE_TO_FIND_DESTINATION_DIRECTORY,
      CollaborationToolsBundle.message("clone.dialog.clone.failed"),
      CollaborationToolsBundle.message("clone.dialog.error.unable.to.find.destination.directory")
    )
  }

  companion object {
    private const val CLONE_UNABLE_TO_CREATE_DESTINATION_DIRECTORY = "gitlab.clone.unable.to.create.destination.directory"
    private const val CLONE_UNABLE_TO_FIND_DESTINATION_DIRECTORY = "gitlab.clone.unable.to.find.destination.directory"
  }
}