package com.intellij.xdebugger.impl.ui.attach.dialog.items.list

import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.attach.XAttachPresentationGroup
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachSelectionIgnoredNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElement
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogProcessItem


internal class AttachToProcessListItem(
  val item: AttachDialogProcessItem) : AttachToProcessElement {

  override fun visit(filters: AttachToProcessElementsFilters): Boolean {
    return filters.selectedFilter.get().canBeAppliedTo(item.getGroups()) && (
      filters.speedSearch.shouldBeShowing(item.processInfo.pid.toString()) ||
      filters.speedSearch.shouldBeShowing(item.processInfo.commandLine))
  }

  override fun getProcessItem(): AttachDialogProcessItem = item
}

internal abstract class AttachToProcessListGroupBase(val groupName: String) : AttachToProcessElement, AttachSelectionIgnoredNode {

  private val relatedNodes = mutableListOf<AttachToProcessListItem>()

  var isFirstGroup: Boolean = false

  fun add(itemNode: AttachToProcessListItem) {
    relatedNodes.add(itemNode)
  }

  fun getNodes(): List<AttachToProcessListItem> = relatedNodes

  override fun visit(filters: AttachToProcessElementsFilters): Boolean {
    return isAcceptedByFilters(filters) &&
           relatedNodes.any { filters.matches(it) }
  }

  abstract fun isAcceptedByFilters(filters: AttachToProcessElementsFilters): Boolean

  abstract fun getOrder(): Int

  override fun getProcessItem(): AttachDialogProcessItem? = null
}

internal class AttachToProcessListGroup(private val presentationGroup: XAttachPresentationGroup<*>) : AttachToProcessListGroupBase(presentationGroup.groupName) {

  override fun isAcceptedByFilters(filters: AttachToProcessElementsFilters): Boolean {
    return filters.selectedFilter.get().canBeAppliedTo(setOf(presentationGroup))
  }

  override fun getOrder(): Int = presentationGroup.order
}

internal class AttachToProcessListRecentGroup : AttachToProcessListGroupBase(
  XDebuggerBundle.message("xdebugger.attach.toLocal.popup.recent")) {
  override fun isAcceptedByFilters(filters: AttachToProcessElementsFilters): Boolean {
    return true
  }

  override fun getOrder(): Int = Int.MIN_VALUE
}