// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.auth.ui.login.LoginModel
import com.intellij.collaboration.messages.CollaborationToolsBundle
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
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabTokenLoginPanelModel
import org.jetbrains.plugins.gitlab.ui.clone.GitLabCloneViewModel.UIState
import org.jetbrains.plugins.gitlab.util.SingleCoroutineLauncher
import java.nio.file.Paths

internal interface GitLabCloneViewModel {
  val accountManager: GitLabAccountManager

  val uiState: Flow<UIState>
  val accountsRefreshRequest: Flow<Set<GitLabAccount>>
  val isLoading: Flow<Boolean>
  val errorLogin: Flow<Throwable?>
  val selectedItem: Flow<GitLabCloneListItem?>

  val loginModel: GitLabTokenLoginPanelModel
  val accountDetailsProvider: GitLabAccountsDetailsProvider

  fun runTask(block: suspend () -> Unit)

  fun selectItem(item: GitLabCloneListItem?)

  fun switchToLoginPanel()

  fun switchToRepositoryList()

  fun doClone(checkoutListener: CheckoutProvider.Listener, directoryPath: String)

  suspend fun collectAccountRepositories(account: GitLabAccount): List<GitLabCloneListItem>

  suspend fun login()

  enum class UIState {
    LOGIN,
    REPOSITORY_LIST
  }
}

internal class GitLabCloneViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  override val accountManager: GitLabAccountManager
) : GitLabCloneViewModel {
  private val vcsNotifier: VcsNotifier = project.service<VcsNotifier>()

  private val cs: CoroutineScope = parentCs.childScope()
  private val taskLauncher: SingleCoroutineLauncher = SingleCoroutineLauncher(cs)

  private val accounts: Flow<Set<GitLabAccount>> = accountManager.accountsState

  private val _uiState: MutableStateFlow<UIState> = MutableStateFlow(UIState.LOGIN)
  override val uiState: Flow<UIState> = _uiState.asSharedFlow()

  override val accountsRefreshRequest: MutableSharedFlow<Set<GitLabAccount>> = MutableSharedFlow()
  override val isLoading: Flow<Boolean> = taskLauncher.busy

  private val _errorLogin: MutableStateFlow<Throwable?> = MutableStateFlow(null)
  override val errorLogin: Flow<Throwable?> = _errorLogin.asSharedFlow()

  private val _selectedItem: MutableStateFlow<GitLabCloneListItem?> = MutableStateFlow(null)
  override val selectedItem: Flow<GitLabCloneListItem?> = _selectedItem.asSharedFlow()

  override val loginModel: GitLabTokenLoginPanelModel = GitLabTokenLoginPanelModel(
    requiredUsername = null,
    uniqueAccountPredicate = accountManager::isAccountUnique
  ).apply {
    serverUri = GitLabServerPath.DEFAULT_SERVER.uri
  }

  override val accountDetailsProvider = GitLabAccountsDetailsProvider(cs) { account ->
    val token = accountManager.findCredentials(account) ?: return@GitLabAccountsDetailsProvider null
    GitLabApiImpl { token }
  }

  init {
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      accounts.collectLatest { accounts ->
        accountsRefreshRequest.emit(accounts)
        if (accounts.isNotEmpty()) {
          _uiState.value = UIState.REPOSITORY_LIST
        }

        accounts.forEach { account ->
          accountManager.getCredentialsFlow(account).collectLatest {
            accountsRefreshRequest.emit(accounts)
          }
        }
      }
    }

    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      loginModel.loginState.collectLatest { loginState ->
        when (loginState) {
          is LoginModel.LoginState.Connected -> {
            val account = GitLabAccount(name = loginState.username, server = loginModel.getServerPath())
            accountManager.updateAccount(account, loginModel.token)
          }
          LoginModel.LoginState.Connecting -> {
            _errorLogin.value = null
          }
          LoginModel.LoginState.Disconnected -> {}
          is LoginModel.LoginState.Failed -> {
            _errorLogin.value = loginState.error
          }
        }
      }
    }
  }

  override fun runTask(block: suspend () -> Unit) = taskLauncher.launch {
    block()
  }

  override fun selectItem(item: GitLabCloneListItem?) {
    _selectedItem.value = item
  }

  override fun switchToLoginPanel() {
    _uiState.value = UIState.LOGIN
  }

  override fun switchToRepositoryList() {
    _uiState.value = UIState.REPOSITORY_LIST
  }

  override fun doClone(checkoutListener: CheckoutProvider.Listener, directoryPath: String) {
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

    val selectedRepository = _selectedItem.value!! as GitLabCloneListItem.Repository
    val selectedUrl = selectedRepository.projectMember.project.httpUrlToRepo
    GitCheckoutProvider.clone(project, Git.getInstance(), checkoutListener, destinationParent, selectedUrl, directoryName, parentDirectory)
  }

  override suspend fun collectAccountRepositories(account: GitLabAccount): List<GitLabCloneListItem> {
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

  override suspend fun login() {
    loginModel.login()
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