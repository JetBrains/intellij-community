// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.progress.EmptyProgressIndicator
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.requests.GithubRequestPagination
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.test.GithubTest

class GithubRequestQueryingTest : GithubTest() {

  override fun setUp() {
    super.setUp()

    mainAccount.executor.execute(
      GithubApiRequests.Organisations.Repos.create(mainAccount.account.server, organisation, "org_repo", "", false))

    mainAccount.executor.execute(
      GithubApiRequests.CurrentUser.Repos.create(mainAccount.account.server, "main_user_repo1", "", false))
    mainAccount.executor.execute(
      GithubApiRequests.CurrentUser.Repos.create(mainAccount.account.server, "main_user_repo2", "", false))
    mainAccount.executor.execute(
      GithubApiRequests.CurrentUser.Repos.create(mainAccount.account.server, "main_user_repo3", "", false))
    mainAccount.executor.execute(
      GithubApiRequests.CurrentUser.Repos.create(mainAccount.account.server, "main_user_repo4", "", false))

    setCurrentAccount(secondaryAccount)
    secondaryAccount.executor.execute(
      GithubApiRequests.CurrentUser.Repos.create(secondaryAccount.account.server, "sec_user_repo1", "", false))

    setCurrentAccount(mainAccount)
  }

  @Throws(Throwable::class)
  fun testLinkPagination() {
    val result = GithubApiPagesLoader
      .loadAll(mainAccount.executor, EmptyProgressIndicator(),
               GithubApiRequests.CurrentUser.Repos.pages(mainAccount.account.server, false, GithubRequestPagination(pageSize = 2))).map {
        it.name
      }

    assertSameElements(result, listOf("main_user_repo1", "main_user_repo2", "main_user_repo3", "main_user_repo4"))
  }

  @Throws(Throwable::class)
  fun testOwnRepos() {
    val result = GithubApiPagesLoader
      .loadAll(mainAccount.executor, EmptyProgressIndicator(),
               GithubApiRequests.CurrentUser.Repos.pages(mainAccount.account.server, false)).map {
        it.name
      }

    assertSameElements(result, "main_user_repo1", "main_user_repo2", "main_user_repo3", "main_user_repo4")
  }

  @Throws(Throwable::class)
  fun testAllRepos() {
    val result = GithubApiPagesLoader
      .loadAll(mainAccount.executor, EmptyProgressIndicator(),
               GithubApiRequests.CurrentUser.Repos.pages(mainAccount.account.server)).map {
        it.name
      }

    assertSameElements(result, "org_repo", "main_user_repo1", "main_user_repo2", "main_user_repo3", "main_user_repo4")
  }
}
