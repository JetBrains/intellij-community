// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.filters

import com.intellij.openapi.components.*
import kotlinx.serialization.Serializable
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsPersistentFiltersHistory.HistoryState

@Service(Service.Level.PROJECT)
@State(name = "GitLabMergeRequestFiltersHistory", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)
internal class GitLabMergeRequestsPersistentFiltersHistory : SerializablePersistentStateComponent<HistoryState>(HistoryState()) {

  @Serializable
  data class HistoryState(
    val history: List<GitLabMergeRequestsFiltersValue> = emptyList(),
    val lastFilter: GitLabMergeRequestsFiltersValue? = null
  )

  var lastFilter: GitLabMergeRequestsFiltersValue?
    get() = state.lastFilter
    set(value) {
      updateState {
        it.copy(lastFilter = value)
      }
    }

  var history: List<GitLabMergeRequestsFiltersValue>
    get() = state.history.toList()
    set(value) {
      updateState {
        it.copy(history = value)
      }
    }
}