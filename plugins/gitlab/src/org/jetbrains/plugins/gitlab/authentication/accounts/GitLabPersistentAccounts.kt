// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.accounts

import com.intellij.collaboration.auth.AccountsRepository
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.SimpleModificationTracker
import kotlinx.serialization.Serializable

@State(name = "GitLabAccounts", storages = [Storage(value = "gitlab.xml")], reportStatistic = false)
internal class GitLabPersistentAccounts
  : AccountsRepository<GitLabAccount>,
    SerializablePersistentStateComponent<GitLabPersistentAccounts.GitLabAccountsState>(GitLabAccountsState()) {

  @Serializable
  data class GitLabAccountsState(var accounts: Set<GitLabAccount> = setOf())

  private val tracker = SimpleModificationTracker()

  override var accounts: Set<GitLabAccount>
    get() = state.accounts.toSet()
    set(value) {
      state.accounts = value.toSet()
      tracker.incModificationCount()
    }

  override fun getStateModificationCount(): Long = tracker.modificationCount
}