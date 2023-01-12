package com.intellij.xdebugger.impl.ui.attach.dialog.items.list

import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.attach.XAttachPresentationGroup
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogProcessItem
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachSelectionIgnoredNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElement
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.separators.TableGroupHeaderSeparator


internal class AttachToProcessListItem(
  val item: AttachDialogProcessItem) : AttachToProcessElement {

  override fun visit(filters: AttachToProcessElementsFilters): Boolean {
    return filters.accept(item)
  }

  override fun getProcessItem(): AttachDialogProcessItem = item
}

internal abstract class AttachToProcessListGroupBase(val groupName: String?) : AttachToProcessElement, AttachSelectionIgnoredNode {

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

  fun getExpectedHeight(): Int {
    return TableGroupHeaderSeparator.getExpectedHeight(isFirstGroup)
  }
}

internal class AttachToProcessListGroup(private val presentationGroup: XAttachPresentationGroup<*>) : AttachToProcessListGroupBase(presentationGroup.groupName) {

  override fun isAcceptedByFilters(filters: AttachToProcessElementsFilters): Boolean {
    return filters.accept(setOf(presentationGroup))
  }

  override fun getOrder(): Int = presentationGroup.order
}

internal class AttachToProcessListRecentGroup : AttachToProcessListGroupBase(
  XDebuggerBundle.message("xdebugger.attach.dialog.recently.attached.message")) {
  override fun isAcceptedByFilters(filters: AttachToProcessElementsFilters): Boolean {
    return true
  }

  override fun getOrder(): Int = Int.MIN_VALUE
}

internal class AttachToProcessOtherItemsGroup : AttachToProcessListGroupBase(XDebuggerBundle.message("xdebugger.attach.dialog.other.processes.message")) {
  override fun isAcceptedByFilters(filters: AttachToProcessElementsFilters): Boolean {
    return getNodes().any { filters.matches(it) }
  }

  override fun getOrder(): Int = Int.MAX_VALUE
}