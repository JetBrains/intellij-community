// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.util.GithubNotifications

/**
 * Handles default Github account for project
 *
 * TODO: auto-detection
 */
@State(name = "GithubDefaultAccount", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class GithubProjectDefaultAccountHolder(private val project: Project,
                                                 private val accountManager: GithubAccountManager) : PersistentStateComponent<AccountState> {
  var account: GithubAccount? = null

  init {
    ApplicationManager.getApplication()
      .messageBus
      .connect(project)
      .subscribe(GithubAccountManager.ACCOUNT_REMOVED_TOPIC, object : AccountRemovedListener {
        override fun accountRemoved(removedAccount: GithubAccount) {
          if (account == removedAccount) account = null
        }
      })
  }

  override fun getState(): AccountState {
    return AccountState().apply { defaultAccountId = account?.id }
  }

  override fun loadState(state: AccountState) {
    account = state.defaultAccountId?.let(::findAccountById)
  }

  private fun findAccountById(id: String): GithubAccount? {
    val account = accountManager.accounts.find { it.id == id }
    if (account == null) runInEdt {
      GithubNotifications.showWarning(project, "Missing Default Github Account", "",
                                      GithubNotifications.getConfigureAction(project))
    }
    return account
  }
}

internal class AccountState {
  var defaultAccountId: String? = null
}

