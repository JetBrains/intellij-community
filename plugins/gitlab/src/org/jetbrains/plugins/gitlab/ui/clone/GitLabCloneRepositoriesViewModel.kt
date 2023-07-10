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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.GitLabApiImpl
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabAccountsDetailsProvider
import org.jetbrains.plugins.gitlab.ui.clone.GitLabCloneRepositoriesViewModel.SearchModel
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Paths

internal interface GitLabCloneRepositoriesViewModel : GitLabCloneViewModel {
  val isLoading: Flow<Boolean>
  val accountsUpdatedRequest: SharedFlow<Set<GitLabAccount>>

  val items: SharedFlow<List<GitLabCloneListItem>>
  val searchValue: SharedFlow<SearchModel>
  val selectedUrl: SharedFlow<String?>

  val accountDetailsProvider: GitLabAccountsDetailsProvider

  fun selectItem(item: GitLabCloneListItem?)

  fun setSearchValue(text: String)

  fun setDirectoryPath(path: String)

  fun doClone(checkoutListener: CheckoutProvider.Listener)

  sealed interface SearchModel {
    class Url(val url: String) : SearchModel
    object Text : SearchModel
  }
}

internal class GitLabCloneRepositoriesViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  private val accountManager: GitLabAccountManager
) : GitLabCloneRepositoriesViewModel {
  private val vcsNotifier: VcsNotifier = project.service<VcsNotifier>()

  private val cs: CoroutineScope = parentCs.childScope()

  private val taskLauncher: SingleCoroutineLauncher = SingleCoroutineLauncher(cs)
  override val isLoading: Flow<Boolean> = taskLauncher.busy

  private val accounts: Flow<Set<GitLabAccount>> = accountManager.accountsState

  private val _accountsUpdatedRequest: MutableSharedFlow<Set<GitLabAccount>> = MutableSharedFlow(replay = 1)
  override val accountsUpdatedRequest: SharedFlow<Set<GitLabAccount>> = _accountsUpdatedRequest.asSharedFlow()

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

  private val directoryPath: MutableStateFlow<String> = MutableStateFlow("")

  override val accountDetailsProvider = GitLabAccountsDetailsProvider(cs) { account ->
    val token = accountManager.findCredentials(account) ?: return@GitLabAccountsDetailsProvider null
    GitLabApiImpl { token }
  }

  init {
    cs.launch {
      accounts.collectLatest { accounts ->
        _accountsUpdatedRequest.emit(accounts)
        coroutineScope {
          accounts.forEach { account ->
            launch {
              accountManager.getCredentialsFlow(account).collectLatest {
                _accountsUpdatedRequest.emit(accounts)
              }
            }
          }
        }
      }
    }

    cs.launch {
      _accountsUpdatedRequest.collectLatest { accounts ->
        taskLauncher.launch {
          _items.value = accounts.flatMap { account ->
            collectRepositoriesByAccount(account)
          }
        }
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

  override fun setDirectoryPath(path: String) {
    directoryPath.value = path
  }

  override fun doClone(checkoutListener: CheckoutProvider.Listener) {
    val selectedUrl = _selectedUrl.value ?: error("Clone button is enabled when repository is not selected")
    val directoryPath = directoryPath.value
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