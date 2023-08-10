// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTree
import javax.swing.JTable
import javax.swing.table.JTableHeader

internal class ExpandableItemsHandlerFactoryImpl : ExpandableItemsHandlerFactory() {
  override fun doInstall(component: JComponent) = when (component) {
    is JList<*> -> ListExpandableItemsHandler(component)
    is JTree -> TreeExpandableItemsHandler(component)
    is JTable -> TableExpandableItemsHandler(component)
    is JTableHeader -> TableHeaderExpandableItemsHandler(component)
    else -> null
  }
}
