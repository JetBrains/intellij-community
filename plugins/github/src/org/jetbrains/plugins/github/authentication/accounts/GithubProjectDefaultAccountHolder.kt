// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.GithubNotificationIdsHolder
import org.jetbrains.plugins.github.util.GithubNotifications
import org.jetbrains.plugins.github.util.GithubUtil

/**
 * Handles default Github account for project
 *
 * TODO: auto-detection
 */
@State(name = "GithubDefaultAccount", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)
internal class GithubProjectDefaultAccountHolder(private val project: Project) : PersistentStateComponent<AccountState> {
  var account: GithubAccount? = null

  override fun getState(): AccountState {
    return AccountState().apply { defaultAccountId = account?.let(GHAccountSerializer::serialize) }
  }

  override fun loadState(state: AccountState) {
    account = state.defaultAccountId?.let(::findAccountById)
  }

  private fun findAccountById(id: String): GithubAccount? {
    val account = GHAccountSerializer.deserialize(id)
    if (account == null) runInEdt {
      val title = GithubBundle.message("accounts.default.missing")
      GithubUtil.LOG.info("${title}; ${""}")
      VcsNotifier.IMPORTANT_ERROR_NOTIFICATION.createNotification(title, NotificationType.WARNING)
        .setDisplayId(GithubNotificationIdsHolder.MISSING_DEFAULT_ACCOUNT)
        .addAction(GithubNotifications.getConfigureAction(project))
        .notify(project)
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
