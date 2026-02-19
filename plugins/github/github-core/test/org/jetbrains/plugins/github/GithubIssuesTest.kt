// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.idea.IJIgnore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.DumbProgressIndicator
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubIssue
import org.jetbrains.plugins.github.issue.GithubIssuesLoadingHelper
import org.jetbrains.plugins.github.test.GithubTest

@IJIgnore(issue = "no server")
class GithubIssuesTest : GithubTest() {
  private val indicator = DumbProgressIndicator.INSTANCE

  private lateinit var repoName: String

  override fun setUp() {
    super.setUp()

    setCurrentAccount(secondaryAccount)
    val executor = secondaryAccount.executor
    val server = secondaryAccount.account.server
    val username = secondaryAccount.username

    val repo = createUserRepo(secondaryAccount)
    repoName = repo.name

    // should probably accept an invitation https://developer.github.com/v3/repos/invitations/#accept-a-repository-invitation
    // but it works so...
    executor.execute(GithubApiRequests.Repos.Collaborators.add(server, username, repoName, mainAccount.username))

    fun issue(title: String,
              body: String? = null,
              milestone: Long? = null,
              labels: List<String>? = null,
              assignees: List<String>? = null) =
      executor.execute(GithubApiRequests.Repos.Issues.create(server, username, repoName, title, body, milestone, labels, assignees))

    fun issueComment(issue: GithubIssue, body: String) =
      executor.execute(GithubApiRequests.Repos.Issues.Comments.create(server, username, repoName, issue.number.toString(), body))

    fun close(issue: GithubIssue) =
      executor.execute(GithubApiRequests.Repos.Issues.updateState(server, username, repoName, issue.number.toString(), false))

    issue("Issue 1", assignees = listOf(secondaryAccount.username))
    issue("issue 2", assignees = listOf(secondaryAccount.username))
    issue("issue 3", assignees = listOf(mainAccount.username)).apply {
      close(this)
    }
    issue("issue 4").apply {
      close(this)
    }
    issue("issue 5")
    issue("bug", assignees = listOf(mainAccount.username))
    issue("error 1234", assignees = listOf(mainAccount.username))
    issue("error 4321", assignees = listOf(mainAccount.username))
    issue("issue 4")
    issue("Keyword issue", body = "abracadabra")
    issue("Commented issue").apply {
      issueComment(this, "commentary")
      issueComment(this, "комментарий")
    }
    issue("Keyword", body = "abracadabra").apply {
      close(this)
    }
    issue("13: Кириллический заголовок", body = "кириллическое тело").apply {
      issueComment(this, "комментарий")
    }
    val markupText = "- item1\n" +
                     "- item2\n" +
                     "- item3\n" +
                     "\n" +
                     "~~Mistaken text~~\n" +
                     "- [x] this is a complete item\n" +
                     "- [ ] this is an incomplete item\n" +
                     "\n" +
                     "@ideatest1\n"
    issue("Issue with markup", body = markupText).apply {
      issueComment(this, markupText)
    }

    setCurrentAccount(mainAccount)
  }

  @Throws(Exception::class)
  fun `test open assigned to main`() {
    loadAndCheck(listOf(6L, 7L, 8L),
                 false, 100, mainAccount.username)
  }

  @Throws(Exception::class)
  fun `test open assigned to second`() {
    loadAndCheck(listOf(1L, 2L),
                 false, 100, secondaryAccount.username)
  }

  @Throws(Exception::class)
  fun `test open assignee unspecified`() {
    loadAndCheck(listOf(1L, 2L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 13L, 14L),
                 false, 100)
  }

  @Throws(Exception::class)
  fun `test all assigned to main`() {
    loadAndCheck(listOf(3L, 6L, 7L, 8L),
                 true, 100, mainAccount.username)
  }

  @Throws(Exception::class)
  fun `test all assigned to second`() {
    loadAndCheck(listOf(1L, 2L),
                 true, 100, secondaryAccount.username)
  }

  @Throws(Exception::class)
  fun `test all assignee unspecified`() {
    loadAndCheck(listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L),
                 true, 100)
  }

  @Throws(Exception::class)
  fun `test open with keyword`() {
    queryAndCheck(listOf(10L),
                  false, null, "abracadabra")
  }

  @Throws(Exception::class)
  fun `test all with keyword`() {
    queryAndCheck(listOf(10L, 12L),
                  true, null, "abracadabra")
  }

  @Throws(Exception::class)
  fun `test all with comment`() {
    queryAndCheck(listOf(11L),
                  true, null, "commentary")
  }

  private fun loadAndCheck(expected: List<Long>, withClosed: Boolean, maximum: Int = 100, assignee: String? = null) {
    retry(LOG) {
      val issues = GithubIssuesLoadingHelper.load(mainAccount.executor, indicator, mainAccount.account.server,
                                                  secondaryAccount.username, repoName, withClosed, maximum, assignee).map {
        it.number
      }
      assertSameElements(issues, expected)
    }
  }

  private fun queryAndCheck(expected: List<Long>, withClosed: Boolean, assignee: String? = null, query: String? = null) {
    retry(LOG) {
      val issues = GithubIssuesLoadingHelper.search(mainAccount.executor, indicator, mainAccount.account.server,
                                                    secondaryAccount.username, repoName, withClosed, assignee, query).map {
        it.number
      }
      assertSameElements(issues, expected)
    }
  }

  companion object {
    private val LOG = logger<GithubIssuesTest>()
  }
}
