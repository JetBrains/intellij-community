// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.collaboration.async.*
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.request.search.GithubIssueSearchType
import org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder
import kotlin.properties.Delegates

internal class GHPRListLoader(
  parentCs: CoroutineScope,
  requestExecutor: GithubApiRequestExecutor,
  repository: GHRepositoryCoordinates,
) {
  private val cs = parentCs.childScope(javaClass.name)

  var searchQuery by Delegates.observable<GHPRSearchQuery?>(null) { _, _, _ ->
    reload()
  }

  private val requestMoreLauncher = SingleCoroutineLauncher(cs.childScope("Request More"))

  private val reloadRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val refreshRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val updateRequests = MutableSharedFlow<Updated<GHPullRequestShort>>(replay = 1)

  private val loader = GraphQLListLoader.startIn(
    cs,
    { it.id },

    requestReloadFlow = reloadRequests,
    requestRefreshFlow = refreshRequests,
    requestChangeFlow = updateRequests
  ) { cursor ->
    val page = GraphQLRequestPagination(afterCursor = cursor, pageSize = 50)
    requestExecutor.executeSuspend(
      GHGQLRequests.PullRequest.search(repository.serverPath, buildQuery(repository.repositoryPath, searchQuery), page)
    ).search
  }

  private val dataStateFlow = loader.resultOrErrorFlow.stateInNow(cs, Result.failure(RuntimeException("Loader has not been initialized yet")))

  val loadedData: StateFlow<List<GHPullRequestShort>> = dataStateFlow.mapState { it.getOrNull() ?: listOf() }
  val error: StateFlow<Throwable?> = loader.resultOrErrorFlow.map { it.exceptionOrNull() }.stateInNow(cs, null)
  val isLoading: StateFlow<Boolean> = loader.isBusyFlow
  val refreshOrReloadRequests: Flow<Unit> = channelFlow {
    launch { reloadRequests.collect { send(it) } }
    launch { refreshRequests.collect { send(it) } }
  }

  fun tryLoadMore() {
    requestMoreLauncher.launch {
      loader.loadMore()
    }
  }

  fun updateData(updater: (GHPullRequestShort) -> GHPullRequestShort?) {
    updateRequests.tryEmit(Updated { updater(it) ?: it })
  }

  fun refresh() {
    refreshRequests.tryEmit(Unit)
  }

  fun reload() {
    cs.launchNow {
      reloadRequests.emit(Unit)
    }
  }

  companion object {
    private fun buildQuery(repoPath: GHRepositoryPath, searchQuery: GHPRSearchQuery?): String {
      return GithubApiSearchQueryBuilder.searchQuery {
        term(GHPRSearchQuery.QualifierName.type.createTerm(GithubIssueSearchType.pr.name))
        term(GHPRSearchQuery.QualifierName.repo.createTerm(repoPath.toString()))
        if (searchQuery != null) {
          for (term in searchQuery.terms) {
            this.term(term)
          }
        }
      }
    }
  }
}
