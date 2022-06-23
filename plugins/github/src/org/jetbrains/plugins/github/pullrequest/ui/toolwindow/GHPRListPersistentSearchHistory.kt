// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.openapi.components.*
import com.intellij.openapi.util.SimpleModificationTracker
import kotlinx.serialization.Serializable

@Service(Service.Level.PROJECT)
@State(name = "GitHubPullRequestSearchHistory", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)
internal class GHPRListPersistentSearchHistory :
  SerializablePersistentStateComponent<GHPRListPersistentSearchHistory.HistoryState>(HistoryState()) {

  @Serializable
  data class HistoryState(var history: List<GHPRListSearchValue> = listOf())

  private val tracker = SimpleModificationTracker()

  var history: List<GHPRListSearchValue>
    get() = state.history.toList()
    set(value) {
      state.history = value.trim(RECENT_SEARCH_FILTERS_LIMIT)
      tracker.incModificationCount()
    }

  override fun getStateModificationCount(): Long = tracker.modificationCount

  companion object {
    private const val RECENT_SEARCH_FILTERS_LIMIT = 10

    private fun <E> List<E>.trim(sizeLimit: Int): List<E> {
      val result = this.toMutableList()
      while (result.size > sizeLimit) {
        result.removeFirst()
      }
      return result
    }
  }
}
