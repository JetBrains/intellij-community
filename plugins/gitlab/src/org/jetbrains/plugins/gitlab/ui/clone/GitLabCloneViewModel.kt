// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.plugins.gitlab.api.GitLabApiImpl
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.SingleCoroutineLauncher

internal interface GitLabCloneViewModel {
  val isLoading: Flow<Boolean>

  val accounts: Flow<Set<GitLabAccount>>
  val selectedItem: Flow<GitLabCloneListItem?>

  fun runTask(block: suspend () -> Unit)

  fun selectItem(item: GitLabCloneListItem?)

  suspend fun collectAccountRepositories(account: GitLabAccount): List<GitLabCloneListItem>
}

internal class GitLabCloneViewModelImpl(
  parentCs: CoroutineScope,
  private val accountManager: GitLabAccountManager
) : GitLabCloneViewModel {
  private val cs: CoroutineScope = parentCs.childScope()
  private val taskLauncher: SingleCoroutineLauncher = SingleCoroutineLauncher(cs)

  override val isLoading: Flow<Boolean> = taskLauncher.busy
  override val accounts: Flow<Set<GitLabAccount>> = accountManager.accountsState

  private val _selectedItem: MutableStateFlow<GitLabCloneListItem?> = MutableStateFlow(null)
  override val selectedItem: Flow<GitLabCloneListItem?> = _selectedItem.asSharedFlow()

  override fun runTask(block: suspend () -> Unit) = taskLauncher.launch {
    block()
  }

  override fun selectItem(item: GitLabCloneListItem?) {
    _selectedItem.value = item
  }

  override suspend fun collectAccountRepositories(account: GitLabAccount): List<GitLabCloneListItem> {
    val token = accountManager.findCredentials(account) ?: return emptyList() // TODO: missing token
    val apiClient = GitLabApiImpl { token }
    val currentUser = apiClient.graphQL.getCurrentUser(account.server) ?: return emptyList() // TODO: expired token
    val accountRepositories = currentUser.projectMemberships.map { projectMember ->
      GitLabCloneListItem(account, projectMember)
    }

    return accountRepositories.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.presentation() })
  }
}