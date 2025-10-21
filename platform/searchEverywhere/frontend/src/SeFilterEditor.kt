// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.platform.searchEverywhere.SeFilterState
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a filter editor for a Search Everywhere tab.
 *
 * @param resultFlow a flow of filter state changes.
 * @function getPresentation returns a presentation of the filter editor.
 */
@ApiStatus.Internal
interface SeFilterEditor {
  val resultFlow: StateFlow<SeFilterState>

  fun getHeaderActions(): List<AnAction>

  fun getSearchFieldActions(): List<AnAction> = emptyList()
}

@ApiStatus.Internal
interface AutoToggleAction {
  /**
   * Automatically toggles the search scope between the everywhere scope
   * and the project scope based on the argument `everywhere`.
   *
   * @param everywhere If `true`, switches to the everywhere scope.
   *                          If `false`, switches to the project scope.
   * @return true if the scope was changed, false otherwise
   */
  fun autoToggle(everywhere: Boolean): Boolean
}