// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.openapi.Disposable
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

@Internal
interface SeItemDataProvider: Disposable {
  val id: SeProviderId
  val displayName: @Nls String

  fun getItems(params: SeParams): Flow<SeItemData>

  suspend fun itemSelected(itemData: SeItemData,
                           modifiers: Int,
                           searchText: String): Boolean

  /**
   * Defines if results can be shown in <i>Find</i> toolwindow.
   */
  suspend fun canBeShownInFindResults(): Boolean

  suspend fun getSearchScopesInfo(): SeSearchScopesInfo?
  suspend fun getTypeVisibilityStates(): List<SeTypeVisibilityStatePresentation>?
}