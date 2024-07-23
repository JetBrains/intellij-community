// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.table.JTableHeader

private class ExpandableItemsHandlerFactoryImpl : ExpandableItemsHandlerFactory() {
  override fun doInstall(component: JComponent) = when (component) {
    is JList<*> -> ListExpandableItemsHandler(component)
    is JTree -> TreeExpandableItemsHandler(component)
    is JTable -> TableExpandableItemsHandler(component)
    is JTableHeader -> TableHeaderExpandableItemsHandler(component)
    else -> null
  }
}
