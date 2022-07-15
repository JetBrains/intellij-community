// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.openapi.components.*
import kotlinx.serialization.Serializable

@Service(Service.Level.PROJECT)
@State(name = "GitHubPullRequestSearchHistory", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)
internal class GHPRListPersistentSearchHistory : SerializablePersistentStateComponent<GHPRListPersistentSearchHistory.HistoryState>(
  HistoryState()) {

  @Serializable
  data class HistoryState(val history: List<GHPRListSearchValue> = emptyList())

  var history: List<GHPRListSearchValue>
    get() = state.history.toList()
    set(value) {
      updateState {
        HistoryState(value.toList())
      }
    }
}
