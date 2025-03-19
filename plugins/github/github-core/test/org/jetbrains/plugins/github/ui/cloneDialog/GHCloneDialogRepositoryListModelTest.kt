package org.jetbrains.plugins.github.ui.cloneDialog

import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.api.data.GithubRepo
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GHCloneDialogRepositoryListModelTest {
  @Test
  fun `repository is still added if found on both user and organisation`() {
    val underTest = GHCloneDialogRepositoryListModel()

    val account = GithubAccount("test")
    val details = GithubAuthenticatedUser()
    val repo = GithubRepo()

    underTest.addRepositories(account, details, listOf(repo))
    underTest.addRepositories(account, details, listOf(repo))

    assertEquals(1, underTest.size)
  }
}
