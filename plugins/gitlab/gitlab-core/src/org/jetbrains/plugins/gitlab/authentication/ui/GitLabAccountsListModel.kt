// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.collaboration.auth.ui.AccountsListModel
import com.intellij.collaboration.auth.ui.MutableAccountsListModel
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount

internal class GitLabAccountsListModel : MutableAccountsListModel<GitLabAccount, String>(),
                                         AccountsListModel.WithDefault<GitLabAccount, String> {
  override var defaultAccount: GitLabAccount? = null
}