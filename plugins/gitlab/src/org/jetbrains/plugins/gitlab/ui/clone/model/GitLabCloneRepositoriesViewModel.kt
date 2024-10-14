// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone.model

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.dvcs.ui.CloneDvcsValidationUtils
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.util.coroutines.childScope
import git4idea.checkout.GitCheckoutProvider
import git4idea.commands.Git
import git4idea.ui.GitShallowCloneViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.dto.GitLabGroupMemberDTO
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabAccountsDetailsProvider
import org.jetbrains.plugins.gitlab.ui.clone.GitLabCloneException
import org.jetbrains.plugins.gitlab.ui.clone.GitLabCloneListItem
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneRepositoriesViewModel.SearchModel
import org.jetbrains.plugins.gitlab.ui.clone.presentation
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Paths

internal interface GitLabCloneRepositoriesViewModel : GitLabClonePanelViewModel {
  val isLoading: Flow<Boolean>
  val accountsUpdatedRequest: SharedFlow<Set<GitLabAccount>>

  val items: SharedFlow<List<GitLabCloneListItem>>
  val searchValue: SharedFlow<SearchModel>
  val selectedUrl: SharedFlow<String?>

  val accountDetailsProvider: GitLabAccountsDetailsProvider

  val shallowCloneVm: GitShallowCloneViewModel

  fun selectItem(item: GitLabCloneListItem?)

  fun setSearchValue(text: String)

  fun setDirectoryPath(path: String)

  fun reload()

  fun doClone(checkoutListener: CheckoutProvider.Listener)

  sealed interface SearchModel {
    class Url(val url: String) : SearchModel
    object Text : SearchModel
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabCloneRepositoriesViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  private val accountManager: GitLabAccountManager,
  private val switchToLoginAction: (GitLabAccount) -> Unit
) : GitLabCloneRepositoriesViewModel {
  private val apiManager: GitLabApiManager = service<GitLabApiManager>()
  private val vcsNotifier: VcsNotifier = VcsNotifier.getInstance(project)

  private val cs: CoroutineScope = parentCs.childScope()

  private val taskLauncher: SingleCoroutineLauncher = SingleCoroutineLauncher(cs)
  override val isLoading: Flow<Boolean> = taskLauncher.busy

  private val reloadRepositoriesRequest: MutableSharedFlow<Unit> = MutableSharedFlow(replay = 1)

  override val accountsUpdatedRequest: SharedFlow<Set<GitLabAccount>> = accountManager.accountsState.transformLatest { accounts ->
    emit(accounts)
    coroutineScope {
      accounts.forEach { account ->
        launch {
          accountManager.getCredentialsFlow(account).collectLatest {
            emit(accounts)
          }
        }
      }
    }
  }.modelFlow(cs, thisLogger())

  private val _selectedItem: MutableStateFlow<GitLabCloneListItem?> = MutableStateFlow(null)

  override val items: SharedFlow<List<GitLabCloneListItem>> = combine(reloadRepositoriesRequest, accountsUpdatedRequest, ::Pair)
    .transformLatest { (_, accounts) ->
      taskLauncher.launch {
        val repositories = accounts.flatMap { account ->
          collectRepositoriesByAccount(account)
        }
        emit(repositories)
      }
    }.modelFlow(cs, thisLogger())

  private val _searchValue: MutableStateFlow<String> = MutableStateFlow("")
  override val searchValue: SharedFlow<SearchModel> = _searchValue.mapState(cs) { text ->
    // TODO: implement ssh "git@"
    try {
      URL(text) // Check URL correctness
      SearchModel.Url(text)
    }
    catch (_: MalformedURLException) {
      SearchModel.Text
    }
  }

  private val _selectedUrl: StateFlow<String?> = combine(searchValue, _selectedItem) { searchValue, selectedItem ->
    when {
      searchValue is SearchModel.Url -> searchValue.url
      selectedItem != null && selectedItem is GitLabCloneListItem.Repository -> selectedItem.project.httpUrlToRepo
      else -> null
    }
  }.stateIn(cs, SharingStarted.Eagerly, initialValue = null)
  override val selectedUrl: SharedFlow<String?> = _selectedUrl

  private val directoryPath: MutableStateFlow<String> = MutableStateFlow("")

  override val shallowCloneVm = GitShallowCloneViewModel()

  override val accountDetailsProvider = GitLabAccountsDetailsProvider(cs, accountManager) { account ->
    val token = accountManager.findCredentials(account) ?: return@GitLabAccountsDetailsProvider null
    apiManager.getClient(account.server) { token }
  }

  override fun selectItem(item: GitLabCloneListItem?) {
    _selectedItem.value = item
  }

  override fun setSearchValue(text: String) {
    _searchValue.value = text
  }

  override fun setDirectoryPath(path: String) {
    directoryPath.value = path
  }

  override fun reload() {
    cs.launch {
      reloadRepositoriesRequest.emit(Unit)
    }
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

    GitCheckoutProvider.clone(
      project,
      Git.getInstance(),
      checkoutListener,
      destinationParent,
      selectedUrl,
      directoryName,
      parentDirectory,
      shallowCloneVm.getShallowCloneOptions(),
    )
  }

  private suspend fun collectRepositoriesByAccount(account: GitLabAccount): List<GitLabCloneListItem> {
    return withContext(Dispatchers.IO) {
      try {
        val token = accountManager.findCredentials(account) ?: return@withContext listOf(
          GitLabCloneListItem.Error(account, GitLabCloneException.MissingAccessToken { switchToLoginAction(account) })
        )
        val apiClient = apiManager.getClient(account.server) { token }
        val currentUser = runCatchingUser { apiClient.graphQL.getCurrentUser() }.getOrElse {
          return@withContext listOf(
            GitLabCloneListItem.Error(account, GitLabCloneException.RevokedToken { switchToLoginAction(account) })
          )
        }
        with(currentUser) {
          (projectMemberships.mapNotNull { it.project } + groupMemberships.flatMap(GitLabGroupMemberDTO::projectMemberships))
            .map { project -> GitLabCloneListItem.Repository(account, project) }
            .distinctBy { repository -> repository.project.fullPath }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.presentation() })
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (_: ConnectException) {
        listOf(GitLabCloneListItem.Error(account, GitLabCloneException.ConnectionError(::reload)))
      }
      catch (e: Throwable) {
        val errorMessage = e.localizedMessage ?: CollaborationToolsBundle.message("clone.dialog.error.load.repositories")
        listOf(GitLabCloneListItem.Error(account, GitLabCloneException.Unknown(errorMessage, ::reload)))
      }
    }
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