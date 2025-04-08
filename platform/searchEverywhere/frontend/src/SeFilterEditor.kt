// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.options.ObservableOptionEditor
import com.intellij.platform.searchEverywhere.SeFilter
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
abstract class SeFilterEditor<T: SeFilter>(initialFilter: T) : ObservableOptionEditor<SeFilterState> {
  protected var filterValue: T = initialFilter
    set(value) {
      field = value
      _resultFlow.value = value.toState()
    }

  private val _resultFlow: MutableStateFlow<SeFilterState?> = MutableStateFlow(initialFilter.toState())
  override val resultFlow: StateFlow<SeFilterState?> = _resultFlow.asStateFlow()

  abstract fun getActions(): List<AnAction>

  override fun getComponent(): JComponent {
    val actionGroup = DefaultActionGroup(getActions())
    val toolbar = ActionManager.getInstance().createActionToolbar("search.everywhere.toolbar", actionGroup, true)
    toolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY)
    val toolbarComponent = toolbar.getComponent()
    toolbarComponent.setOpaque(false)
    toolbarComponent.setBorder(JBUI.Borders.empty(2, 18, 2, 9))
    return toolbarComponent
  }

  override fun result(): SeFilterState {
    return resultFlow.value ?: SeFilterState.Empty
  }
}