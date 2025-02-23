// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.files

import com.intellij.openapi.options.ObservableOptionEditor
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.api.SeFilterData
import com.intellij.platform.searchEverywhere.api.SeTab
import com.intellij.platform.searchEverywhere.frontend.SeTabHelper
import com.intellij.platform.searchEverywhere.providers.files.SeFilesFilterData
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.JComponent

@Internal
class SeFilesTab(val helper: SeTabHelper): SeTab {
  override val name: String get() = "Files"
  override val shortName: String get() = name

  override fun getItems(params: SeParams): Flow<SeItemData> =
    helper.getItems(params)

  override fun getFilterEditor(): ObservableOptionEditor<SeFilterData> = SeFilesFilterEditor()

  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean {
    return helper.itemSelected(item, modifiers, searchText)
  }
}

@Internal
class SeFilesFilterEditor : ObservableOptionEditor<SeFilterData> {
  private var current: SeFilesFilterData? = null

  private val _resultFlow: MutableStateFlow<SeFilterData?> = MutableStateFlow(current?.toFilterData())
  override val resultFlow: StateFlow<SeFilterData?> = _resultFlow.asStateFlow()

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

  override fun result(): SeFilterData {
    return resultFlow.value ?: SeFilterData(emptyMap())
  }
}
