// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.openapi.Disposable
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeResultEvent
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Experimental
@ApiStatus.Internal
interface SeTab: Disposable {
  val name: @Nls String
  val shortName: @Nls String
  val id: String

  /**
   * Retrieves a flow of search result events based on the provided parameters.
   * May be called from a background thread. Returns a [Flow] that emits the results asynchronously.
   *
   * @param params the parameters containing the query text and optional filter data for the search operation
   * @return a flow emitting events related to search results, such as additions, replacements, or skips.
   */
  fun getItems(params: SeParams): Flow<SeResultEvent>

  fun getFilterEditor(): SeFilterEditor?

  suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean
}