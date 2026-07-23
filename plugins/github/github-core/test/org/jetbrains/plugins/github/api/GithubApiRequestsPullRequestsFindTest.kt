// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code
// is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination
import org.junit.Test

class GithubApiRequestsPullRequestsFindTest {
  private val repository = GHRepositoryCoordinates(GithubServerPath("github.com"), GHRepositoryPath("owner", "repo"))

  @Test
  fun `find targets the pulls endpoint`() {
    val url = GithubApiRequests.Repos.PullRequests.find(repository, GithubIssueState.open, headRef = "owner:feature").url
    assertTrue(url, url.contains("/repos/owner/repo/pulls"))
  }

  @Test
  fun `find omits per_page when no pagination is requested`() {
    val url = GithubApiRequests.Repos.PullRequests.find(repository, GithubIssueState.open, headRef = "owner:feature").url
    assertFalse(url, url.contains("per_page"))
  }

  @Test
  fun `find bounds the page size when pagination is requested`() {
    val url = GithubApiRequests.Repos.PullRequests
      .find(repository, GithubIssueState.open, headRef = "owner:feature", pagination = GithubRequestPagination(pageSize = 2))
      .url
    assertTrue(url, url.contains("per_page=2"))
    assertTrue(url, url.contains("page=1"))
  }
}
