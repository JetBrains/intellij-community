// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.CodeReviewDomainEntity
import com.intellij.collaboration.util.ComputableSequence
import com.intellij.collaboration.util.ListPart
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GHReactionContent
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.request.search.GithubIssueSearchType
import org.jetbrains.plugins.github.api.executeSuspend
import org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRMentionableUsersProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRCreationService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRDetailsService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRPersistentInteractionState
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.ui.icons.GHAvatarIconsProvider

@ApiStatus.Internal
@CodeReviewDomainEntity // repository
class GHPRDataContext internal constructor(
  val scope: CoroutineScope,
  private val requestExecutor: GithubApiRequestExecutor,
  internal val dataProviderRepository: GHPRDataProviderRepository,
  val securityService: GHPRSecurityService,
  val repositoryDataService: GHPRRepositoryDataService,
  internal val creationService: GHPRCreationService,
  internal val detailsService: GHPRDetailsService,
  internal val reactionsService: GHReactionsService,
  internal val htmlImageLoader: AsyncHtmlImageLoader,
  internal val avatarIconsProvider: GHAvatarIconsProvider,
  internal val mentionableUsersProvider: GHPRMentionableUsersProvider,
  internal val reactionIconsProvider: IconsProvider<GHReactionContent>,
  internal val interactionState: GHPRPersistentInteractionState,
) {
  internal fun getListLoader(searchQuery: GHPRSearchQuery?): ComputableSequence<ListPart<GHPullRequestShort>> {
    val repository = repositoryDataService.repositoryCoordinates
    val query = buildPRSearchQuery(repository.repositoryPath, searchQuery)
    return ComputableSequence.byPointer(GraphQLRequestPagination(pageSize = PR_PAGE_SIZE)) { pagination ->
      val response = requestExecutor.executeSuspend(
        GHGQLRequests.PullRequest.search(repository.serverPath, query, pagination)
      )
      val nextPage = if (response.pageInfo.hasNextPage) {
        GraphQLRequestPagination(afterCursor = response.pageInfo.endCursor, pageSize = PR_PAGE_SIZE)
      }
      else {
        null
      }
      response.nodes to nextPage
    }
  }
}

private const val PR_PAGE_SIZE = 50

private fun buildPRSearchQuery(repoPath: GHRepositoryPath, searchQuery: GHPRSearchQuery?): String {
  return GithubApiSearchQueryBuilder.searchQuery {
    term(GHPRSearchQuery.QualifierName.type.createTerm(GithubIssueSearchType.pr.name))
    term(GHPRSearchQuery.QualifierName.repo.createTerm(repoPath.toString()))
    if (searchQuery != null) {
      for (term in searchQuery.terms) {
        term(term)
      }
    }
  }
}