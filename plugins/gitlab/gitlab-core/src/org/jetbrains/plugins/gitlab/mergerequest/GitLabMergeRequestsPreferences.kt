// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.util.CollectableSerializablePersistentStateComponent
import com.intellij.openapi.components.*
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable

@Service(Service.Level.PROJECT)
@State(name = "GitLabMergeRequestsSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)
internal class GitLabMergeRequestsPreferences
  : CollectableSerializablePersistentStateComponent<GitLabMergeRequestsPreferences.SettingsState>(SettingsState()) {

  @Serializable
  data class SettingsState(
    val selectedUrlAndAccountId: Pair<String, String>? = null,
    val showEventsInTimeline: Boolean = true,
    val highlightDiffLinesInEditor: Boolean = true,
    val usedAsDraftSubmitActionLast: Boolean = true,
    val editorReviewEnabled: Boolean = true,
    val changesGrouping: Set<String> = setOf(ChangesGroupingSupport.DIRECTORY_GROUPING, ChangesGroupingSupport.MODULE_GROUPING),
    val editorReviewViewOption: DiscussionsViewOption = DiscussionsViewOption.UNRESOLVED_ONLY
  )

  var selectedUrlAndAccountId: Pair<String, String>?
    get() = state.selectedUrlAndAccountId
    set(value) {
      updateStateAndEmit {
        it.copy(selectedUrlAndAccountId = value)
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

  var diffReviewViewOption : DiscussionsViewOption
    get () = state.editorReviewViewOption
    set (value) {
        updateStateAndEmit {
          it.copy(editorReviewViewOption = value)
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