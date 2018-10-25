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

import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.util.Comparing
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.github.issue.GithubIssuesLoadingHelper
import org.jetbrains.plugins.github.test.GithubTest
import java.util.*

/**
 * @author Aleksey Pivovarov
 */
class GithubIssuesTest : GithubTest() {
  private val indicator = DumbProgressIndicator.INSTANCE

  @Throws(Exception::class)
  fun testAssigneeIssues1() {
    val result = GithubIssuesLoadingHelper.load(mainAccount.executor, indicator, mainAccount.account.server,
                                                secondaryAccount.username, REPO_NAME, false, 100, mainAccount.username)
    val issues = ContainerUtil.map(result) { githubIssue -> githubIssue.number }

    val expected = Arrays.asList(6L, 7L, 8L)

    assertTrue(Comparing.haveEqualElements(issues, expected))
  }

  @Throws(Exception::class)
  fun testAssigneeIssues2() {
    val result = GithubIssuesLoadingHelper.load(mainAccount.executor, indicator, mainAccount.account.server,
                                                secondaryAccount.username, REPO_NAME, false, 100, secondaryAccount.username)
    val issues = ContainerUtil.map(result) { githubIssue -> githubIssue.number }

    val expected = Arrays.asList(1L, 2L)

    assertTrue(Comparing.haveEqualElements(issues, expected))
  }

  @Throws(Exception::class)
  fun testAssigneeIssues3() {
    val result = GithubIssuesLoadingHelper.load(mainAccount.executor, indicator, mainAccount.account.server,
                                                secondaryAccount.username, REPO_NAME, false, 100)
    val issues = ContainerUtil.map(result) { githubIssue -> githubIssue.number }

    val expected = Arrays.asList(1L, 2L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 13L, 14L)

    assertTrue(Comparing.haveEqualElements(issues, expected))
  }

  @Throws(Exception::class)
  fun testAssigneeIssues4() {
    val result = GithubIssuesLoadingHelper.load(mainAccount.executor, indicator, mainAccount.account.server,
                                                secondaryAccount.username, REPO_NAME, true, 100, mainAccount.username)
    val issues = ContainerUtil.map(result) { githubIssue -> githubIssue.number }

    val expected = Arrays.asList(3L, 6L, 7L, 8L)

    assertTrue(Comparing.haveEqualElements(issues, expected))
  }

  @Throws(Exception::class)
  fun testAssigneeIssues5() {
    val result = GithubIssuesLoadingHelper.load(mainAccount.executor, indicator, mainAccount.account.server,
                                                secondaryAccount.username, REPO_NAME, true, 100, secondaryAccount.username)
    val issues = ContainerUtil.map(result) { githubIssue -> githubIssue.number }

    val expected = Arrays.asList(1L, 2L)

    assertTrue(Comparing.haveEqualElements(issues, expected))
  }

  @Throws(Exception::class)
  fun testAssigneeIssues6() {
    val result = GithubIssuesLoadingHelper.load(mainAccount.executor, indicator, mainAccount.account.server,
                                                secondaryAccount.username, REPO_NAME, true, 100)
    val issues = ContainerUtil.map(result) { githubIssue -> githubIssue.number }

    val expected = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L)

    assertTrue(Comparing.haveEqualElements(issues, expected))
  }

  @Throws(Exception::class)
  fun testQueriedIssues1() {
    val result = GithubIssuesLoadingHelper.search(mainAccount.executor, indicator, mainAccount.account.server,
                                                  secondaryAccount.username, REPO_NAME, true, null, "abracadabra")
    val issues = ContainerUtil.map(result) { githubIssue -> githubIssue.number }

    val expected = Arrays.asList(10L, 12L)

    assertTrue(Comparing.haveEqualElements(issues, expected))
  }

  @Throws(Exception::class)
  fun testQueriedIssues2() {
    val result = GithubIssuesLoadingHelper.search(mainAccount.executor, indicator, mainAccount.account.server,
                                                  secondaryAccount.username, REPO_NAME, true, null, "commentary")
    val issues = ContainerUtil.map(result) { githubIssue -> githubIssue.number }

    val expected = Arrays.asList(11L)

    assertTrue(Comparing.haveEqualElements(issues, expected))
  }

  @Throws(Exception::class)
  fun testQueriedIssues3() {
    val result = GithubIssuesLoadingHelper.search(mainAccount.executor, indicator, mainAccount.account.server,
                                                  secondaryAccount.username, REPO_NAME, false, null, "abracadabra")
    val issues = ContainerUtil.map(result) { githubIssue -> githubIssue.number }

    val expected = Arrays.asList(10L)

    assertTrue(Comparing.haveEqualElements(issues, expected))
  }

  companion object {
    private const val REPO_NAME = "IssuesTest"
  }
}
