// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ui.cloneDialog

import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import javax.swing.ListModel

interface GHCloneDialogRepositoryListLoader {
  val loading: Boolean
  val listModel: ListModel<GHRepositoryListItem>

  fun loadRepositories(account: GithubAccount)
  fun clear(account: GithubAccount)
  fun addLoadingStateListener(listener: () -> Unit)
}