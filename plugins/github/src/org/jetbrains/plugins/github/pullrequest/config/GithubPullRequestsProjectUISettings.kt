// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.config

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GHAccountSerializer
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHProjectRepositoriesManager

@Service
@State(name = "GithubPullRequestsUISettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)
class GithubPullRequestsProjectUISettings(private val project: Project)
  : PersistentStateComponentWithModificationTracker<GithubPullRequestsProjectUISettings.SettingsState> {

  private var state: SettingsState = SettingsState()

  class SettingsState : BaseState() {
    var selectedUrlAndAccountId by property<UrlAndAccount?>(null) { it == null }
    var recentSearchFilters by list<String>()
    var recentNewPullRequestHead by property<RepoCoordinatesHolder?>(null) { it == null }
  }

  @Deprecated("Deprecated when moving to single-tab pull requests")
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  fun getHiddenUrls(): Set<String> = emptySet()

  var selectedRepoAndAccount: Pair<GHGitRepositoryMapping, GithubAccount>?
    get() {
      val (url, accountId) = state.selectedUrlAndAccountId ?: return null
      val repo = project.service<GHProjectRepositoriesManager>().knownRepositories.find {
        it.gitRemote.url == url
      } ?: return null
      val account = GHAccountSerializer.deserialize(accountId) ?: return null
      return repo to account
    }
    set(value) {
      state.selectedUrlAndAccountId = value?.let { (repo, account) ->
        UrlAndAccount(repo.gitRemote.url, GHAccountSerializer.serialize(account))
      }
    }

  fun getRecentSearchFilters(): List<String> = state.recentSearchFilters.toList()

  fun addRecentSearchFilter(searchFilter: String) {
    val addExisting = state.recentSearchFilters.remove(searchFilter)
    state.recentSearchFilters.add(0, searchFilter)

    if (state.recentSearchFilters.size > RECENT_SEARCH_FILTERS_LIMIT) {
      state.recentSearchFilters.removeLastOrNull()
    }

    if (!addExisting) {
      state.intIncrementModificationCount()
    }
  }

  var recentNewPullRequestHead: GHRepositoryCoordinates?
    get() = state.recentNewPullRequestHead?.let { GHRepositoryCoordinates(it.server, GHRepositoryPath(it.owner, it.repository)) }
    set(value) {
      state.recentNewPullRequestHead = value?.let { RepoCoordinatesHolder(it) }
    }

  override fun getStateModificationCount() = state.modificationCount
  override fun getState() = state
  override fun loadState(state: SettingsState) {
    this.state = state
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<GithubPullRequestsProjectUISettings>()

    private const val RECENT_SEARCH_FILTERS_LIMIT = 10

    class UrlAndAccount private constructor() {

      var url: String = ""
      var accountId: String = ""

      constructor(url: String, accountId: String) : this() {
        this.url = url
        this.accountId = accountId
      }

      operator fun component1() = url
      operator fun component2() = accountId
    }

    class RepoCoordinatesHolder private constructor() {

      var server: GithubServerPath = GithubServerPath.DEFAULT_SERVER
      var owner: String = ""
      var repository: String = ""

      constructor(coordinates: GHRepositoryCoordinates): this() {
        server = coordinates.serverPath
        owner = coordinates.repositoryPath.owner
        repository = coordinates.repositoryPath.repository
      }
    }
  }
}