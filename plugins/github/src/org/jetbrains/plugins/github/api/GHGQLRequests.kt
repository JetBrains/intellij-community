// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import org.jetbrains.plugins.github.api.GithubApiRequest.Post.GQLQuery
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.graphql.GHGQLRequestPagination
import org.jetbrains.plugins.github.api.data.graphql.query.GHGQLSearchQueryResponse

object GHGQLRequests {
  object PullRequest {
    fun findOne(server: GithubServerPath, repoOwner: String, repoName: String, number: Long): GQLQuery<GHPullRequest?> {
      return GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.findPullRequest,
                                      mapOf("repoOwner" to repoOwner,
                                            "repoName" to repoName,
                                            "number" to number),
                                      GHPullRequest::class.java,
                                      "repository", "pullRequest")
    }

    fun search(server: GithubServerPath, query: String, pagination: GHGQLRequestPagination? = null)
      : GQLQuery<GHGQLSearchQueryResponse<GHPullRequestShort>> {

      return GQLQuery.Parsed(server.toGraphQLUrl(), GHGQLQueries.issueSearch,
                             mapOf("query" to query,
                                   "pageSize" to pagination?.pageSize,
                                   "cursor" to pagination?.afterCursor),
                             PRSearch::class.java)
    }

    private class PRSearch(search: SearchConnection<GHPullRequestShort>)
      : GHGQLSearchQueryResponse<GHPullRequestShort>(search)
  }
}