// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultJBList
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListItemRow
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListModel
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListRow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeSelectionListener(
  initialSelectionState: SeSelectionState?,
  val resultList: SeResultJBList<SeResultListRow>,
  val resultListModel: SeResultListModel,
) {
  private var selectionState: SeSelectionState? = initialSelectionState

  fun getIndexToSelect(maxVisibleRowCount: Int, currentPattern: String, isInitialSearchPattern: Boolean, isEndEvent: Boolean): Int {
    if (selectionState == null) return 0

    val selectedItemData = selectionState!!.selectedItem
    if (resultListModel.size >= 1 && (resultListModel.get(0) as? SeResultListItemRow)?.item?.contentEquals(selectedItemData) == true) {
      return 0
    }

    val effectiveModelSize = resultList.getEffectiveModelSize()
    if (resultList.selectedIndex >= 1 || effectiveModelSize == 0) {
      return -1
    }

    if (isEndEvent || (isInitialSearchPattern && selectionState!!.pattern != currentPattern)) {
      selectionState = null
      return 0
    }
    else {
      val searchRange = 0..minOf(effectiveModelSize - 1, maxVisibleRowCount - 1)
      val matchingIndex = searchRange.firstOrNull { i ->
        (resultListModel.get(i) as? SeResultListItemRow)?.item?.contentEquals(selectedItemData) == true
      }

      if (matchingIndex != null) {
        return matchingIndex
      }

      if (searchRange.last >= maxVisibleRowCount - 1) {
        selectionState = null
      }

      return 0
    }
  }

  fun saveSelectionState(currentPattern: String) {
    if (resultList.selectedIndex == -1) {
      return
    }

    val itemDataToSave = (resultListModel.get(resultList.selectedIndex) as? SeResultListItemRow)?.item
    if (itemDataToSave != null) {
      selectionState = SeSelectionState(currentPattern, itemDataToSave)
    }
  }

  fun getSelectionState(): SeSelectionState? {
    return selectionState
  }
}

@ApiStatus.Internal
class SeSelectionState(val pattern: String, val selectedItem: SeItemData)
