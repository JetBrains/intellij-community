// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import org.jetbrains.plugins.github.api.data.GithubRepo
import org.jetbrains.plugins.github.api.data.GithubUser
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount

sealed class GHRepositoryListItem(
  val account: GithubAccount
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GHRepositoryListItem

    return account == other.account
  }

  override fun hashCode(): Int {
    return account.hashCode()
  }

  class Repo(
    account: GithubAccount,
    val user: GithubUser,
    val repo: GithubRepo
  ) : GHRepositoryListItem(account) {

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false
      if (!super.equals(other)) return false

      other as Repo

      if (user != other.user) return false
      if (repo != other.repo) return false

      return true
    }

    override fun hashCode(): Int {
      var result = super.hashCode()
      result = 31 * result + user.hashCode()
      result = 31 * result + repo.hashCode()
      return result
    }
  }

  class Error(account: GithubAccount, val error: Throwable) : GHRepositoryListItem(account) {

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false
      if (!super.equals(other)) return false

      other as Error

      return error == other.error
    }

    override fun hashCode(): Int {
      var result = super.hashCode()
      result = 31 * result + error.hashCode()
      return result
    }
  }
}