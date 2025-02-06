// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend.model

import org.jetbrains.annotations.ApiStatus
import javax.swing.DefaultListSelectionModel
import javax.swing.ListModel

@ApiStatus.Internal
class PreservingSelectionModel(val dataModel: ListModel<*>) : DefaultListSelectionModel() {

  // Attempt to preserve selection when items are removed
  override fun removeIndexInterval(index0: Int, index1: Int) {
    super.removeIndexInterval(index0, index1)
    val indexBeforeFirstSelected = index0 - 1
    val indexAfterLastSelected = index1

    val nextIndexToSelect = if (indexBeforeFirstSelected >= 0) indexBeforeFirstSelected else if (indexAfterLastSelected >= 0) indexAfterLastSelected else null
    if (nextIndexToSelect != null && nextIndexToSelect in 0 until dataModel.size) {
      super.addSelectionInterval(nextIndexToSelect, nextIndexToSelect)
    }
  }

  // Avoid extending existing selection when another element is inserted next to the selected one
  override fun insertIndexInterval(index: Int, length: Int, before: Boolean) {
    super.insertIndexInterval(index, length, before)
    for (addedIndex in index..index + length) {
      if (isSelectedIndex(addedIndex)) {
        super.removeSelectionInterval(addedIndex, addedIndex)
      }
    }
  }
}