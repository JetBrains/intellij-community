package com.intellij.xdebugger.impl.ui.attach.dialog.items

import com.intellij.openapi.observable.properties.AtomicLazyProperty
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.xdebugger.attach.XAttachPresentationGroup
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogDebuggersFilter
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogProcessItem
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogElementNode

class AttachToProcessElementsFilters(private val selectedFilter: AtomicLazyProperty<AttachDialogDebuggersFilter>) {

  private val speedSearch = SpeedSearch().apply {
    updatePattern("")
  }

  private val cache = mutableMapOf<AttachDialogElementNode, Boolean>()

  fun matches(node: AttachDialogElementNode): Boolean {
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

  fun updatePattern(filterValue: String) {
    speedSearch.updatePattern(filterValue)
  }

  fun accept(item: AttachDialogProcessItem): Boolean {
    return accept(item.getGroups()) && (speedSearch.shouldBeShowing(item.indexedString) || item.commandLineText.contains(speedSearch.filter ?: "", ignoreCase = true))
  }

  fun accept(item: Set<XAttachPresentationGroup<*>>): Boolean {
    return selectedFilter.get().canBeAppliedTo(item)
  }
}