// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend.model

import com.intellij.openapi.diagnostic.fileLogger
import org.jetbrains.annotations.ApiStatus
import javax.swing.DefaultListSelectionModel
import javax.swing.ListModel

private val LOG by lazy { fileLogger() }

@ApiStatus.Internal
class PreservingSelectionModel(val dataModel: ListModel<*>) : DefaultListSelectionModel() {

  // Attempt to preserve selection when items are removed
  override fun removeIndexInterval(index0: Int, index1: Int) {
    LOG.debug("Switcher remove: Removing index interval: $index0-$index1")
    super.removeIndexInterval(index0, index1)
    val indexBeforeFirstSelected = index0
    val indexAfterLastSelected = index1

    val nextIndexToSelect = if (indexBeforeFirstSelected >= 0) indexBeforeFirstSelected else if (indexAfterLastSelected >= 0) indexAfterLastSelected else null
    val nextIndexToSelectConsideringModelSize = nextIndexToSelect?.coerceAtMost(dataModel.size - 1)
    if (nextIndexToSelectConsideringModelSize != null) {
      LOG.debug("Switcher remove: select index interval: $nextIndexToSelectConsideringModelSize-$nextIndexToSelectConsideringModelSize")
      super.addSelectionInterval(nextIndexToSelectConsideringModelSize, nextIndexToSelectConsideringModelSize)
    }
  }

  // Avoid extending existing selection when another element is inserted next to the selected one
  override fun insertIndexInterval(index: Int, length: Int, before: Boolean) {
    LOG.debug("Switcher insert: insert index interval: $index with length $length before $before")
    super.insertIndexInterval(index, length, before)
    for (addedIndex in index..index + length) {
      if (isSelectedIndex(addedIndex)) {
        LOG.debug("Switcher insert: remove selection interval: $index with length $length before $before")
        super.removeSelectionInterval(addedIndex, addedIndex)
      }
    }
  }
}