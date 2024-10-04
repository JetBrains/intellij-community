// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone.model

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.async.collectBatches
import com.intellij.collaboration.async.mapModelsToViewModels
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.components.service
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.request.getCloneableProjects
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.ui.clone.GitLabCloneException
import org.jetbrains.plugins.gitlab.ui.clone.GitLabCloneListItem
import java.net.ConnectException

/**
 * Represents a list of 'clone items' (could be an error or a list of repositories) associated
 * to a single account. Meaning the account is able to clone each repository in the list.
 */
internal interface GitLabCloneRepositoriesForAccountViewModel {
  val account: GitLabAccount

  val isLoading: StateFlow<Boolean>
  val items: StateFlow<List<GitLabCloneListItem>>

  fun reload()
}

private class GitLabCloneRepositoriesForAccountViewModelImpl(
  parentCs: CoroutineScope,
  private val accountManager: GitLabAccountManager,
  override val account: GitLabAccount,
) : GitLabCloneRepositoriesForAccountViewModel {
  private val cs = parentCs.childScope(javaClass.name)

  private val reloadSignal = MutableSharedFlow<Unit>(1)
  private val apiManager = service<GitLabApiManager>()

  private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  @OptIn(ExperimentalCoroutinesApi::class)
  override val items: StateFlow<List<GitLabCloneListItem>> =
    reloadSignal.withInitial(Unit).transformLatest { _ ->

        try {
          _isLoading.value = true
          val token = accountManager.findCredentials(account) ?: run {
            emit(listOf(GitLabCloneListItem.Error(account, GitLabCloneException.MissingAccessToken(account))))
          return@transformLatest
        }
        val apiClient = apiManager.getClient(account.server) { token }
        apiClient.graphQL.getCloneableProjects()
          .map { l -> l.map { GitLabCloneListItem.Repository(account, it) } }
          .collectBatches()
          .collect { emit(it) }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (_: ConnectException) {
        emit(listOf(GitLabCloneListItem.Error(account, GitLabCloneException.ConnectionError(account))))
      }
      catch (e: Throwable) {
          val errorMessage = e.localizedMessage ?: CollaborationToolsBundle.message("clone.dialog.error.load.repositories")
          emit(listOf(GitLabCloneListItem.Error(account, GitLabCloneException.Unknown(account, errorMessage))))
      }
      finally {
        _isLoading.value = false
      }
    }
      .flowOn(Dispatchers.IO)
      .stateIn(cs, SharingStarted.Eagerly, listOf())

  init {
    cs.launch {
      accountManager.getCredentialsFlow(account).collectLatest {
        reloadSignal.emit(Unit)
      }
    }
  }

  override fun reload() {
    cs.launch {
      reloadSignal.emit(Unit)
    }
  }
}

/**
 * Represents the full list of repositories that can be cloned generated per account.
 */
internal interface GitLabCloneRepositoriesListViewModel {
  val isLoading: StateFlow<Boolean>
  val allAccounts: StateFlow<List<GitLabAccount>>
  val allItems: StateFlow<List<GitLabCloneListItem>>

  fun reload()
  fun reload(account: GitLabAccount)
}

internal class GitLabCloneRepositoriesListViewModelImpl(
  parentCs: CoroutineScope,
  accountManager: GitLabAccountManager,
) : GitLabCloneRepositoriesListViewModel {
  private val cs = parentCs.childScope(javaClass.name)

  private val reloadSignal = MutableSharedFlow<Unit>(1)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val listsPerAccount = reloadSignal.withInitial(Unit).flatMapLatest { _ ->
    accountManager.accountsState.mapModelsToViewModels<GitLabAccount, GitLabCloneRepositoriesForAccountViewModel> { account ->
      GitLabCloneRepositoriesForAccountViewModelImpl(this, accountManager, account)
    }
  }.stateIn(cs, SharingStarted.Eagerly, listOf())

  @OptIn(ExperimentalCoroutinesApi::class)
  override val isLoading: StateFlow<Boolean> = listsPerAccount.flatMapLatest { vms ->
    combine(vms.map { model -> model.isLoading }) {
      it.any { it }
    }
  }.stateIn(cs, SharingStarted.Eagerly, false)

  override val allAccounts: StateFlow<List<GitLabAccount>> = listsPerAccount
    .map { vms -> vms.map { it.account } }
    .stateIn(cs, SharingStarted.Eagerly, listOf())

  @OptIn(ExperimentalCoroutinesApi::class)
  override val allItems: StateFlow<List<GitLabCloneListItem>> = listsPerAccount.flatMapLatest { vms ->
    combine(vms.map { model -> model.items }) {
      it.flatMap { it }
    }
  }.stateIn(cs, SharingStarted.Eagerly, listOf())

  override fun reload() {
    cs.launch {
      reloadSignal.emit(Unit)
    }
  }

  override fun reload(account: GitLabAccount) {
    cs.launch {
      listsPerAccount.value.find { it.account == account }?.reload()
    }
  }
}