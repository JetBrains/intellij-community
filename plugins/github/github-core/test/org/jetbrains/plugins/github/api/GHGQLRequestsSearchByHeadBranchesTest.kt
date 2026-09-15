// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery.QualifierName
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery.Term
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GHGQLRequestsSearchByHeadBranchesTest {
  private val repository = GHRepositoryCoordinates(GithubServerPath.DEFAULT_SERVER, GHRepositoryPath("my-org", "my-repo"))

  @Test
  fun `test an OR term wraps its members in parentheses joined by OR`() {
    val or = Term.Or(listOf(
      QualifierName.head.createTerm("\"branch-a\""),
      QualifierName.head.createTerm("\"branch-b\""),
      QualifierName.head.createTerm("\"branch-c\""),
    ))

    assertEquals("""(head:"branch-a" OR head:"branch-b" OR head:"branch-c")""", or.toString())
  }

  @Test
  fun `test an OR term with a single member still gets parentheses`() {
    val or = Term.Or(listOf(QualifierName.head.createTerm("\"branch-a\"")))

    assertEquals("""(head:"branch-a")""", or.toString())
  }

  @Test
  fun `test the search query groups head branches with OR in a single request`() {
    val request = GHGQLRequests.PullRequest.searchByHeadBranches(
      repository, listOf("branch-a", "branch-b", "branch-c")
    )

    assertEquals(
      """repo:my-org/my-repo type:pr state:open (head:"branch-a" OR head:"branch-b" OR head:"branch-c")""",
      request.variablesObject["query"]
    )
  }

  @Test
  fun `test the page size matches the number of head branches`() {
    val request = GHGQLRequests.PullRequest.searchByHeadBranches(repository, listOf("branch-a", "branch-b"))

    assertEquals(2, request.variablesObject["pageSize"])
  }

  @Test
  fun `test the page size is capped at 100 even with more head branches`() {
    val headBranchRefs = (1..150).map { "branch-$it" }

    val request = GHGQLRequests.PullRequest.searchByHeadBranches(repository, headBranchRefs)

    assertEquals(100, request.variablesObject["pageSize"])
  }
}
