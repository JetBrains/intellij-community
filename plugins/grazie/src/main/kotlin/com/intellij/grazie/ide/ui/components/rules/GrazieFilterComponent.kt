// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.components.rules

import com.intellij.packageDependencies.ui.TreeExpansionMonitor
import com.intellij.ui.FilterComponent
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.tree.DefaultTreeModel

class GrazieFilterComponent(private val tree: GrazieRulesTree, filterName: String,
                            searchFieldName: String) : FilterComponent(filterName, 10) {
  private val expansionMonitor = TreeExpansionMonitor.install(tree)

  init {
    // for UI tests to find search field
    popupLocationComponent.name = searchFieldName
  }

  override fun filter() {
    expansionMonitor.freeze()

    tree.filter(filter)
    (tree.model as DefaultTreeModel).reload()
    TreeUtil.expandAll(tree)

    if (filter.isNullOrBlank()) {
      TreeUtil.collapseAll(tree, 0)
      expansionMonitor.restore()
    }
    else {
      expansionMonitor.unfreeze()
    }
  }
}
