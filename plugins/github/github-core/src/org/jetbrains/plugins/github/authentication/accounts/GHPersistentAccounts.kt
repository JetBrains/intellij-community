// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts

import com.intellij.collaboration.auth.ObservableAccountsRepository
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import kotlinx.coroutines.flow.MutableStateFlow

@State(name = "GithubAccounts", storages = [
  Storage(value = "github.xml"),
  Storage(value = "github_settings.xml", deprecated = true)
], reportStatistic = false, category = SettingsCategory.TOOLS)
internal class GHPersistentAccounts
  : ObservableAccountsRepository<GithubAccount>,
    PersistentStateComponent<Array<GithubAccount>> {

  override var accounts: Set<GithubAccount>
    get() = accountsFlow.value
    set(value) {
      accountsFlow.value = value
    }

  override val accountsFlow = MutableStateFlow(emptySet<GithubAccount>())

  override fun getState(): Array<GithubAccount> = accountsFlow.value.toTypedArray()

  override fun loadState(state: Array<GithubAccount>) {
    accountsFlow.value = state.toSet()
  }

  override fun noStateLoaded() {
    loadState(emptyArray())
  }

}