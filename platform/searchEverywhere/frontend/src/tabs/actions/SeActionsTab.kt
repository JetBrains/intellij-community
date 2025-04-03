// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.CheckBoxSearchEverywhereToggleAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.options.ObservableOptionEditor
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeActionsFilterData
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class SeActionsTab(private val delegate: SeTabDelegate): SeTab {
  override val name: String get() = IdeBundle.message("search.everywhere.group.name.actions")
  override val shortName: String get() = name
  override val id: String get() = "ActionSearchEverywhereContributor"

  override fun getItems(params: SeParams): Flow<SeResultEvent> = delegate.getItems(params)
  override fun getFilterEditor(): ObservableOptionEditor<SeFilterState> = SeActionsFilterEditor()

  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean {
    return delegate.itemSelected(item, modifiers, searchText)
  }

  override fun dispose() {
    Disposer.dispose(delegate)
  }
}

@ApiStatus.Internal
class SeActionsFilterEditor : ObservableOptionEditor<SeFilterState> {
  private var current: SeActionsFilterData? = null
    set(value) {
      field = value
      _resultFlow.value = value?.toFilterData()
    }

  private val _resultFlow: MutableStateFlow<SeFilterState?> = MutableStateFlow(current?.toFilterData())
  override val resultFlow: StateFlow<SeFilterState?> = _resultFlow.asStateFlow()

  fun getActions(): List<AnAction> {
    return listOf<AnAction>(object : CheckBoxSearchEverywhereToggleAction(IdeBundle.message("checkbox.disabled.included")) {
      override fun isEverywhere(): Boolean {
        return current?.includeDisabled ?: false
      }

      override fun setEverywhere(state: Boolean) {
        current = SeActionsFilterData(state)
      }
    })
  }

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
