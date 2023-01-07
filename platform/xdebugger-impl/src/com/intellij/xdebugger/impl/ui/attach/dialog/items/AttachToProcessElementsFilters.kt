package com.intellij.xdebugger.impl.ui.attach.dialog.items

import com.intellij.openapi.observable.properties.AtomicLazyProperty
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogDebuggersFilter

internal class AttachToProcessElementsFilters(val speedSearch: SpeedSearch, val selectedFilter: AtomicLazyProperty<AttachDialogDebuggersFilter>) {
  private val cache = mutableMapOf<AttachToProcessElement, Boolean>()

  fun matches(node: AttachToProcessElement): Boolean {
    val cachedValue = cache[node]
    if (cachedValue != null) {
      return cachedValue
    }
    val result = node.visit(this)
    cache[node] = result
    return result
  }

  fun clear() {
    cache.clear()
  }
}