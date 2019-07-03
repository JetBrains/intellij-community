// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.progress.EmptyProgressIndicator
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubRepo
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination
import org.jetbrains.plugins.github.api.data.request.Type
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.test.GithubTest

class GithubRequestQueryingTest : GithubTest() {

  private val RETRIES = 2

  private lateinit var orgRepo: GithubRepo
  private lateinit var mainRepos: Set<GithubRepo>
  private lateinit var secondaryRepos: Set<GithubRepo>

  override fun setUp() {
    super.setUp()

    orgRepo = createOrgRepo()

    mainRepos = setOf(
      createUserRepo(mainAccount),
      createUserRepo(mainAccount),
      createUserRepo(mainAccount),
      createUserRepo(mainAccount)
    )

    setCurrentAccount(secondaryAccount)
    secondaryRepos = setOf(createUserRepo(secondaryAccount))

    setCurrentAccount(mainAccount)
  }

  @Throws(Throwable::class)
  fun testLinkPagination() {
    retry {
      val result = GithubApiPagesLoader
        .loadAll(mainAccount.executor, EmptyProgressIndicator(),
                 GithubApiRequests.CurrentUser.Repos.pages(mainAccount.account.server,
                                                           type = Type.OWNER,
                                                           pagination = GithubRequestPagination(pageSize = 2)))

      assertContainsElements(result, mainRepos)
      assertDoesntContain(result, secondaryRepos)
    }
  }

  @Throws(Throwable::class)
  fun testOwnRepos() {
    retry {
      val result = GithubApiPagesLoader
        .loadAll(mainAccount.executor, EmptyProgressIndicator(),
                 GithubApiRequests.CurrentUser.Repos.pages(mainAccount.account.server,
                                                           type = Type.OWNER))

      assertContainsElements(result, mainRepos)
      assertDoesntContain(result, orgRepo)
      assertDoesntContain(result, secondaryRepos)

    }
  }

  @Throws(Throwable::class)
  fun testAllRepos() {
    retry {
      val result = GithubApiPagesLoader
        .loadAll(mainAccount.executor, EmptyProgressIndicator(),
                 GithubApiRequests.CurrentUser.Repos.pages(mainAccount.account.server))

      assertContainsElements(result, mainRepos)
      assertContainsElements(result, orgRepo)
      assertDoesntContain(result, secondaryRepos)
    }
  }

  private fun retry(action: () -> Unit) {
    var exception: Exception? = null
    for (i in 1..RETRIES) {
      try {
        action()
        exception = null
        break
      }
      catch (e: Exception) {
        exception = e
      }
    }
    exception?.let { throw it }
  }
}
