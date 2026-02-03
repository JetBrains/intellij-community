// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Represents a Search Everywhere tab.
 * This tab lives on the frontend and communicates with the backend via remote api.
 * For simplier implementation inherit from [com.intellij.platform.searchEverywhere.frontend.tabs.SeDefaultTabBase].
 * For more complex scenarios use [com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate].
 *
 * see [com.intellij.platform.searchEverywhere.frontend.tabs.SeDefaultTabBase]
 * see [com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate]
 */
@ApiStatus.Experimental
interface SeTab : Disposable {
  val name: @Nls String
  val id: String

  /**
   * Priority of the tab from 0 to 1000. The higher the priority, the closer the position to left side of the popup.
   */
  val priority: Int

  val isIndexingDependent: Boolean get() = false

  /**
   * Retrieves a flow of search result events based on the provided parameters.
   * May be called from a background thread. Returns a [Flow] that emits the results asynchronously.
   *
   * @param params the parameters containing the query text and optional filter data for the search operation
   * @return a flow emitting events related to search results, such as additions, replacements, or skips.
   */
  fun getItems(params: SeParams): Flow<SeResultEvent>

  /**
   * Retrieves a filter editor for the tab, if available.
   * @return a filter editor instance or null if no filter editor is available
   */
  suspend fun getFilterEditor(): SeFilterEditor?

  /**
   * Handles the selection of an item in the search results.
   * @param item the selected item data
   * @param modifiers the keyboard modifiers used during the selection
   * @param searchText the current search text
   * @return true if the search popup should be closed, false otherwise
   */
  suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean

  suspend fun getEmptyResultInfo(context: DataContext): SeEmptyResultInfo? = null

  suspend fun canBeShownInFindResults(): Boolean

  suspend fun openInFindToolWindow(session: SeSession, params: SeParams, initEvent: AnActionEvent): Boolean = false

  /**
   * Perform the action provided by the extended info inside the item presentation
   * @return true if the popup should be closed, false otherwise
   */
  suspend fun performExtendedAction(item: SeItemData): Boolean

  suspend fun essentialProviderIds(): Set<SeProviderId> = emptySet()

  /**
   * Retrieves the updated presentation for an item, if available.
   * @return a SeItemPresentation instance or null if no updated presentation is available
   */
  suspend fun getUpdatedPresentation(item: SeItemData): SeItemPresentation? = null

  suspend fun isPreviewEnabled(): Boolean

  suspend fun getPreviewInfo(itemData: SeItemData): SePreviewInfo?

  suspend fun isExtendedInfoEnabled(): Boolean

  suspend fun isCommandsSupported(): Boolean
}