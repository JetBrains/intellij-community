// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.util.CollectableSerializablePersistentStateComponent
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport
import git4idea.remote.hosting.knownRepositories
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

@Service(Service.Level.PROJECT)
@State(name = "GitLabMergeRequestsSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)
internal class GitLabMergeRequestsPreferences(private val project: Project)
  : CollectableSerializablePersistentStateComponent<GitLabMergeRequestsPreferences.SettingsState>(SettingsState()) {

  @Serializable
  data class SettingsState(
    val selectedUrlAndAccountId: Pair<String, String>? = null,
    val showEventsInTimeline: Boolean = true,
    val highlightDiffLinesInEditor: Boolean = true,
    val usedAsDraftSubmitActionLast: Boolean = true,
    val editorReviewEnabled: Boolean = true,
    val changesGrouping: Set<String> = setOf(ChangesGroupingSupport.DIRECTORY_GROUPING, ChangesGroupingSupport.MODULE_GROUPING)
  )

  var selectedRepoAndAccount: Pair<GitLabProjectMapping, GitLabAccount>?
    get() {
      val (url, accountId) = state.selectedUrlAndAccountId ?: return null
      val repo = project.service<GitLabProjectsManager>().knownRepositories.find {
        it.remote.url == url
      } ?: return null
      val account = service<GitLabAccountManager>().accountsState.value.find {
        it.id == accountId
      } ?: return null
      return repo to account
    }
    set(value) {
      updateStateAndEmit {
        val saved = value?.let { (repo, account) -> repo.remote.url to account.id }
        it.copy(selectedUrlAndAccountId = saved)
      }
    }

  var showEventsInTimeline: Boolean
    get() = state.showEventsInTimeline
    set(value) {
      updateStateAndEmit {
        it.copy(showEventsInTimeline = value)
      }
    }

  var highlightDiffLinesInEditor: Boolean
    get() = state.highlightDiffLinesInEditor
    set(value) {
      updateStateAndEmit {
        it.copy(highlightDiffLinesInEditor = value)
      }
    }
  val highlightDiffLinesInEditorState: StateFlow<Boolean> = stateFlow.mapState { it.highlightDiffLinesInEditor }

  var usedAsDraftSubmitActionLast: Boolean
    get() = state.usedAsDraftSubmitActionLast
    set(value) {
      updateStateAndEmit {
        it.copy(usedAsDraftSubmitActionLast = value)
      }
    }
  val usedAsDraftSubmitActionLastState: StateFlow<Boolean> = stateFlow.mapState { it.usedAsDraftSubmitActionLast }

  var editorReviewEnabled: Boolean
    get() = state.editorReviewEnabled
    set(value) {
      updateStateAndEmit {
        it.copy(editorReviewEnabled = value)
      }
    }

  var changesGrouping: Set<String>
    get() = state.changesGrouping
    set(value) {
      updateStateAndEmit {
        it.copy(changesGrouping = value)
      }
    }
  val changesGroupingState: StateFlow<Set<String>> = stateFlow.mapState { it.changesGrouping }
}