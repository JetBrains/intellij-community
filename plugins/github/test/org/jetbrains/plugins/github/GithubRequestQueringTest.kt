/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.test.GithubTest
import org.junit.Assume.assumeNotNull
import java.util.*

/**
 * @author Aleksey Pivovarov
 */
class GithubRequestQueringTest : GithubTest() {

  override fun beforeTest() {
    assumeNotNull(myAccount2)
  }

  @Throws(Throwable::class)
  fun testLinkPagination() {
    val availableRepos = GithubApiPagesLoader
      .loadAll(myExecutor2, EmptyProgressIndicator(), GithubApiRequests.CurrentUser.Repos.pages(myAccount2.server, false))
    val realData = ArrayList<String>()
    for (info in availableRepos) {
      realData.add(info.name)
    }

    val expectedData = ArrayList<String>()
    for (i in 1..251) {
      expectedData.add(i.toString())
    }

    UsefulTestCase.assertContainsElements(realData, expectedData)
  }

  @Throws(Throwable::class)
  fun testOwnRepos() {
    val result = GithubApiPagesLoader
      .loadAll(myExecutor, EmptyProgressIndicator(), GithubApiRequests.CurrentUser.Repos.pages(myAccount.server, false))

    TestCase.assertTrue(ContainerUtil.exists(result) { it -> it.name == "example" })
    TestCase.assertTrue(ContainerUtil.exists(result) { it -> it.name == "PullRequestTest" })
    TestCase.assertFalse(ContainerUtil.exists(result) { it -> it.name == "org_repo" })
  }

  @Throws(Throwable::class)
  fun testAllRepos() {
    val result = GithubApiPagesLoader
      .loadAll(myExecutor, EmptyProgressIndicator(), GithubApiRequests.CurrentUser.Repos.pages(myAccount.server))

    TestCase.assertTrue(ContainerUtil.exists(result) { it -> it.name == "example" })
    TestCase.assertTrue(ContainerUtil.exists(result) { it -> it.name == "PullRequestTest" })
    TestCase.assertTrue(ContainerUtil.exists(result) { it -> it.name == "org_repo" })
  }
}
