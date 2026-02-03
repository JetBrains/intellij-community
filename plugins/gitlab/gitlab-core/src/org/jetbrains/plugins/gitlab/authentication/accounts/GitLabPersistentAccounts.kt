// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.accounts

import com.intellij.collaboration.auth.ObservableAccountsRepository
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

@State(name = "GitLabAccounts",
       category = SettingsCategory.TOOLS,
       exportable = true,
       storages = [Storage(value = "gitlab.xml", roamingType = RoamingType.DISABLED)], reportStatistic = false)
internal class GitLabPersistentAccounts : ObservableAccountsRepository<GitLabAccount>,
                                          SerializablePersistentStateComponent<GitLabPersistentAccounts.GitLabAccountsState>(
                                            GitLabAccountsState()) {

  @Serializable
  data class GitLabAccountsState(val accounts: Set<GitLabAccount> = emptySet())

  override var accounts: Set<GitLabAccount>
    get() = state.accounts.toSet()
    set(value) {
      synchronized(this) {
        updateState {
          GitLabAccountsState(value.toSet())
        }
        accountsFlow.value = value
      }
    }

  override val accountsFlow = MutableStateFlow(emptySet<GitLabAccount>())

  override fun loadState(state: GitLabAccountsState) {
    synchronized(this) {
      super.loadState(state)

      accountsFlow.value = state.accounts
    }
  }

  override fun noStateLoaded() {
    loadState(GitLabAccountsState())
  }
}