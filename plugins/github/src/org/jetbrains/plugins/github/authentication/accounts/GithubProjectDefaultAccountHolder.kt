// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.GithubNotifications

/**
 * Handles default Github account for project
 *
 * TODO: auto-detection
 */
@State(name = "GithubDefaultAccount", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)
internal class GithubProjectDefaultAccountHolder(private val project: Project) : PersistentStateComponent<AccountState> {
  var account: GithubAccount? = null

  override fun getState(): AccountState {
    return AccountState().apply { defaultAccountId = account?.id }
  }

  override fun loadState(state: AccountState) {
    account = state.defaultAccountId?.let(::findAccountById)
  }

  private fun findAccountById(id: String): GithubAccount? {
    val account = service<GithubAccountManager>().accounts.find { it.id == id }
    if (account == null) runInEdt {
      GithubNotifications.showWarning(project, GithubBundle.message("accounts.default.missing"), "",
                                      GithubNotifications.getConfigureAction(project))
    }
    return account
  }

  class RemovalListener(private val project: Project) : AccountRemovedListener {
    override fun accountRemoved(removedAccount: GithubAccount) {
      val holder = project.service<GithubProjectDefaultAccountHolder>()
      if (holder.account == removedAccount) holder.account = null
    }
  }
}

internal class AccountState {
  var defaultAccountId: String? = null
}

