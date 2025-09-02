// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.utils

import com.intellij.platform.searchEverywhere.SeFilter
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class SeFilterEditorBase<T: SeFilter>(initialFilter: T) : SeFilterEditor {
  protected var filterValue: T = initialFilter
    set(value) {
      field = value
      _resultFlow.value = value.toState()
    }

  private val _resultFlow: MutableStateFlow<SeFilterState> = MutableStateFlow(initialFilter.toState())
  override val resultFlow: StateFlow<SeFilterState> = _resultFlow.asStateFlow()
}