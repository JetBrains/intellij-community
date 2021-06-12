// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts

import com.intellij.collaboration.auth.AccountsPersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Not using a [com.intellij.collaboration.auth.SimpleAccountsPersistentStateComponent] because of my stupid decision to use
 * [Array] instead of [com.intellij.openapi.components.BaseState]
 */
@State(name = "GithubAccounts", storages = [
  Storage(value = "github.xml"),
  Storage(value = "github_settings.xml", deprecated = true)
], reportStatistic = false)
internal class GHPersistentAccounts
  : AccountsPersistentStateComponent<GithubAccount, Array<GithubAccount>> {

  private var state = emptyArray<GithubAccount>()

  override var accounts: Set<GithubAccount>
    get() = state.toSet()
    set(value) {
      state = value.toTypedArray()
    }

  override fun getState(): Array<GithubAccount> = state

  override fun loadState(state: Array<GithubAccount>) {
    this.state = state
  }
}