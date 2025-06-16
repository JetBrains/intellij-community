// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog.items

import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogAllDebuggersFilter
import java.awt.Component
import java.awt.Graphics

internal class AttachDialogEmptyText(private val owner: JBTable, private val filters: AttachToProcessElementsFilters): StatusText(owner) {

  init {
    owner.putClientProperty(UIUtil.NOT_IN_HIERARCHY_COMPONENTS, wrappedFragmentsIterable)
  }

  override fun paint(owner: Component?, g: Graphics?) {
    secondaryComponent.clear()
    if (filters.selectedFilter.get() !is AttachDialogAllDebuggersFilter) {
      appendSecondaryText(XDebuggerBundle.message("xdebugger.reset.filtration.by.process.message"), SimpleTextAttributes.LINK_ATTRIBUTES) {
        filters.selectedFilter.set(AttachDialogAllDebuggersFilter)
      }
    }
    super.paint(owner, g)
  }

  override fun isStatusVisible(): Boolean = owner.isEmpty
}