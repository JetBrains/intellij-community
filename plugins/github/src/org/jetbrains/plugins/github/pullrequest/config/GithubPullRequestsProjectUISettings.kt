// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.config

import com.intellij.collaboration.async.mapState
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.knownRepositories
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GHAccountSerializer
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

@Service(Service.Level.PROJECT)
@State(name = "GithubPullRequestsUISettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)
class GithubPullRequestsProjectUISettings(private val project: Project)
  : PersistentStateComponentWithModificationTracker<GithubPullRequestsProjectUISettings.SettingsState> {

  private val stateOfState = MutableStateFlow(SettingsState())

  class SettingsState : BaseState() {
    var selectedUrlAndAccountId by property<UrlAndAccount?>(null) { it == null }
    var recentNewPullRequestHead by property<RepoCoordinatesHolder?>(null) { it == null }
    var reviewCommentPreferred by property(true) { !it }
  }

  var selectedRepoAndAccount: Pair<GHGitRepositoryMapping, GithubAccount>?
    get() {
      val (url, accountId) = state.selectedUrlAndAccountId ?: return null
      val repo = project.service<GHHostedRepositoriesManager>().knownRepositories.find {
        it.remote.url == url
      } ?: return null
      val account = GHAccountSerializer.deserialize(accountId) ?: return null
      return repo to account
    }
    set(value) {
      state.selectedUrlAndAccountId = value?.let { (repo, account) ->
        UrlAndAccount(repo.remote.url, GHAccountSerializer.serialize(account))
      }
    }

  var recentNewPullRequestHead: GHRepositoryCoordinates?
    get() = state.recentNewPullRequestHead?.let { GHRepositoryCoordinates(it.server, GHRepositoryPath(it.owner, it.repository)) }
    set(value) {
      state.recentNewPullRequestHead = value?.let { RepoCoordinatesHolder(it) }
    }

  var reviewCommentsPreferred: Boolean
    get() = state.reviewCommentPreferred
    set(value) {
      state.reviewCommentPreferred = value
    }

  val reviewCommentsPreferredState: StateFlow<Boolean> = stateOfState.mapState { it.reviewCommentPreferred }

  override fun getStateModificationCount(): Long = state.modificationCount
  override fun getState(): SettingsState = stateOfState.value
  override fun loadState(state: SettingsState) {
    stateOfState.value = state
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GithubPullRequestsProjectUISettings = project.service<GithubPullRequestsProjectUISettings>()

    class UrlAndAccount private constructor() {

      @Suppress("MemberVisibilityCanBePrivate")
      var url: String = ""
      @Suppress("MemberVisibilityCanBePrivate")
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