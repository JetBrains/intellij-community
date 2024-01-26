// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.config

import com.intellij.collaboration.async.mapState
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.GithubServerPathSerializer
import org.jetbrains.plugins.github.authentication.accounts.GHAccountSerializer
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount

@Service(Service.Level.PROJECT)
@State(name = "GithubPullRequestsUISettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)
class GithubPullRequestsProjectUISettings(private val project: Project)
  : SerializablePersistentStateComponent<GithubPullRequestsProjectUISettings.SettingsState>(SettingsState()) {

  private val stateOfState = MutableStateFlow(SettingsState())

  @Serializable
  data class SettingsState(
    val selectedUrlAndAccountId: UrlAndAccount? = null,
    val recentNewPullRequestHead: RepoCoordinatesHolder? = null,
    val reviewCommentPreferred: Boolean = true,
    val editorReviewEnabled: Boolean = true,
    val highlightDiffLinesInEditor: Boolean = false
  )

  var selectedUrlAndAccount: Pair<String, GithubAccount>?
    get() {
      val (url, accountId) = state.selectedUrlAndAccountId ?: return null
      val account = GHAccountSerializer.deserialize(accountId) ?: return null
      return url to account
    }
    set(value) = updateAndSetState {
      it.copy(selectedUrlAndAccountId = value?.let { (repo, account) ->
        UrlAndAccount(repo, GHAccountSerializer.serialize(account))
      })
    }

  var recentNewPullRequestHead: GHRepositoryCoordinates?
    get() = state.recentNewPullRequestHead?.let { GHRepositoryCoordinates(it.server, GHRepositoryPath(it.owner, it.repository)) }
    set(value) = updateAndSetState {
      it.copy(recentNewPullRequestHead = value?.let { RepoCoordinatesHolder(it) })
    }

  var reviewCommentsPreferred: Boolean
    get() = state.reviewCommentPreferred
    set(value) = updateAndSetState {
      it.copy(reviewCommentPreferred = value)
    }

  var editorReviewEnabled: Boolean
    get() = state.editorReviewEnabled
    set(value) = updateAndSetState {
      it.copy(editorReviewEnabled = value)
    }

  var highlightDiffLinesInEditor: Boolean
    get() = state.highlightDiffLinesInEditor
    set(value) = updateAndSetState {
      it.copy(highlightDiffLinesInEditor = value)
    }

  val reviewCommentsPreferredState: StateFlow<Boolean> = stateOfState.mapState { it.reviewCommentPreferred }
  val editorReviewEnabledState: StateFlow<Boolean> = stateOfState.mapState { it.editorReviewEnabled }
  val highlightDiffLinesInEditorState: StateFlow<Boolean> = stateOfState.mapState { it.highlightDiffLinesInEditor }

  override fun loadState(state: SettingsState) {
    super.loadState(state)
    stateOfState.value = state
  }

  private inline fun updateAndSetState(updateFunction: (currentState: SettingsState) -> SettingsState) {
    stateOfState.value = updateState(updateFunction)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GithubPullRequestsProjectUISettings = project.service<GithubPullRequestsProjectUISettings>()

    @Serializable
    data class UrlAndAccount(val url: String, val accountId: String)

    @Serializable
    data class RepoCoordinatesHolder(
      @Serializable(with = GithubServerPathSerializer::class)
      val server: GithubServerPath,
      val owner: String,
      val repository: String
    ) {
      constructor(coordinates: GHRepositoryCoordinates) : this(
        coordinates.serverPath,
        coordinates.repositoryPath.owner,
        coordinates.repositoryPath.repository
      )
    }
  }
}