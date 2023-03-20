// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.accounts

import com.intellij.collaboration.auth.AccountsRepository
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import kotlinx.serialization.Serializable

@State(name = "GitLabAccounts", storages = [Storage(value = "gitlab.xml")], reportStatistic = false)
internal class GitLabPersistentAccounts : AccountsRepository<GitLabAccount>,
                                          SerializablePersistentStateComponent<GitLabPersistentAccounts.GitLabAccountsState>(
                                            GitLabAccountsState()) {

  @Serializable
  data class GitLabAccountsState(val accounts: Set<GitLabAccount> = emptySet())

  override var accounts: Set<GitLabAccount>
    get() = state.accounts.toSet()
    set(value) {
      updateState {
        GitLabAccountsState(value.toSet())
      }
    }
}