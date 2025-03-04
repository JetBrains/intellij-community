// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.files

import com.intellij.openapi.options.ObservableOptionEditor
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.providers.files.SeFilesFilterData
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.JComponent

@Internal
class SeFilesTab(private val delegate: SeTabDelegate): SeTab {
  override val name: String get() = "Files"
  override val shortName: String get() = name

  override fun getItems(params: SeParams): Flow<SeResultEvent> =
    delegate.getItems(params)

  override fun getFilterEditor(): ObservableOptionEditor<SeFilterState> = SeFilesFilterEditor()

  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean {
    return delegate.itemSelected(item, modifiers, searchText)
  }
}

@Internal
class SeFilesFilterEditor : ObservableOptionEditor<SeFilterState> {
  private var current: SeFilesFilterData? = null

  private val _resultFlow: MutableStateFlow<SeFilterState?> = MutableStateFlow(current?.toFilterData())
  override val resultFlow: StateFlow<SeFilterState?> = _resultFlow.asStateFlow()

  override fun getComponent(): JComponent {
    return panel {
      row {
        val checkBox = checkBox("Project only")

        checkBox.component.model.isSelected = current?.isProjectOnly ?: false
        checkBox.onChanged {
          if (current?.isProjectOnly != it.isSelected) {
            current = SeFilesFilterData(it.isSelected)
            _resultFlow.value = current?.toFilterData()
          }
        }
      }
    }
  }

  override fun result(): SeFilterState {
    return resultFlow.value ?: SeFilterState.Empty
  }
}
