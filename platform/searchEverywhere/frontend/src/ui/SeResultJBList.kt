// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.ui.components.JBList
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import javax.swing.ListModel

@ApiStatus.Internal
class SeResultJBList<E : SeResultListRow>(model: ListModel<E>) : JBList<E>(model) {
  var isProgrammaticSelectionChange: Boolean = false
    private set

  /**
   * Runs [block] with [isProgrammaticSelectionChange] set, so that any [javax.swing.event.ListSelectionEvent]
   * fired while [block] mutates the model or the selection is not treated as a user action.
   */
  fun <T> withProgrammaticSelectionChange(block: () -> T): T {
    val prev = isProgrammaticSelectionChange
    isProgrammaticSelectionChange = true
    try {
      return block()
    }
    finally {
      isProgrammaticSelectionChange = prev
    }
  }

  /**
   * Returns the number of items in the result list, excluding the loading indicator.
   */
  fun getEffectiveModelSize(): Int {
    val modelSize = model.size
    if (modelSize == 0) return 0

    // Check if the last element is SeResultListMoreRow (loading indicator)
    val lastElement = model.getElementAt(modelSize - 1)
    return if (lastElement is SeResultListMoreRow) {
      modelSize - 1
    } else {
      modelSize
    }
  }

  @RequiresEdt
  fun autoSelectIndex(index: Int) {
    withProgrammaticSelectionChange { selectedIndex = index }
  }
}