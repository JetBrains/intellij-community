// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.test.GithubTest
import java.util.*

class GithubRequestQueryingTest : GithubTest() {

  @Throws(Throwable::class)
  fun testLinkPagination() {
    val availableRepos = GithubApiPagesLoader
      .loadAll(secondaryAccount.executor, EmptyProgressIndicator(),
               GithubApiRequests.CurrentUser.Repos.pages(secondaryAccount.account.server, false))
    val realData = ArrayList<String>()
    for (info in availableRepos) {
      realData.add(info.name)
    }

    val expectedData = ArrayList<String>()
    for (i in 1..251) {
      expectedData.add(i.toString())
    }

    assertContainsElements(realData, expectedData)
  }

  @Throws(Throwable::class)
  fun testOwnRepos() {
    val result = GithubApiPagesLoader
      .loadAll(mainAccount.executor, EmptyProgressIndicator(),
               GithubApiRequests.CurrentUser.Repos.pages(mainAccount.account.server, false))

    assertTrue(ContainerUtil.exists(result) { it -> it.name == "example" })
    assertTrue(ContainerUtil.exists(result) { it -> it.name == "PullRequestTest" })
    assertFalse(ContainerUtil.exists(result) { it -> it.name == "org_repo" })
  }

  @Throws(Throwable::class)
  fun testAllRepos() {
    val result = GithubApiPagesLoader
      .loadAll(mainAccount.executor, EmptyProgressIndicator(),
               GithubApiRequests.CurrentUser.Repos.pages(mainAccount.account.server))

    assertTrue(ContainerUtil.exists(result) { it -> it.name == "example" })
    assertTrue(ContainerUtil.exists(result) { it -> it.name == "PullRequestTest" })
    assertTrue(ContainerUtil.exists(result) { it -> it.name == "org_repo" })
  }
}
