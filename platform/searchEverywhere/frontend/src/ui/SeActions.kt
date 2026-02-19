// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
object SeActions {
  const val SELECT_ITEM: @NonNls String = "SearchEverywhere.SelectItem"
  const val SWITCH_TO_NEXT_TAB: @NonNls String = "SearchEverywhere.NextTab"
  const val SWITCH_TO_PREV_TAB: @NonNls String = "SearchEverywhere.PrevTab"
  const val AUTOCOMPLETE_COMMAND: @NonNls String = "SearchEverywhere.CompleteCommand"
  const val NAVIGATE_TO_NEXT_GROUP: @NonNls String = "SearchEverywhere.NavigateToNextGroup"
  const val NAVIGATE_TO_PREV_GROUP: @NonNls String = "SearchEverywhere.NavigateToPrevGroup"
}