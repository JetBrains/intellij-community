// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.grammar.tabs.rules.component.rules

import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.GrazieTreeComponent
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.allRules
import com.intellij.packageDependencies.ui.TreeExpansionMonitor
import com.intellij.ui.FilterComponent
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.tree.DefaultTreeModel

internal class GrazieRulesTreeFilter(private val tree: GrazieTreeComponent) : FilterComponent("GRAZIE_RULE_FILTER", 10) {
  private val expansionMonitor = TreeExpansionMonitor.install(tree)

  override fun filter() {
    expansionMonitor.freeze()

    filter(filter)

    (tree.model as DefaultTreeModel).reload()

    if (filter.isNullOrBlank()) {
      TreeUtil.collapseAll(tree, 0)
      expansionMonitor.restore()
    }
    else {
      TreeUtil.expandAll(tree)
      expansionMonitor.unfreeze()
    }
  }

  private fun filter(filterString: String?) {
    if (filterString.isNullOrBlank()) {
      tree.resetTreeModel(allRules())
      return
    }

    tree.resetTreeModel(
      allRules().map { (lang, rules) ->
        lang to rules.filter {
          lang.nativeName.contains(filterString, true) ||
          it.categories.any { cat -> cat.contains(filterString, true) } ||
          it.presentableName.contains(filterString, true) ||
          it.searchableDescription.contains(filterString, true)
        }
      }.toMap().filterValues { it.isNotEmpty() }
    )
  }
}
