// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.grammar.tabs.rules.component.rules

import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.GrazieTreeComponent
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.packageDependencies.ui.TreeExpansionMonitor
import com.intellij.ui.FilterComponent
import com.intellij.util.ui.tree.TreeUtil
import java.util.*
import javax.swing.tree.DefaultTreeModel

internal class GrazieRulesTreeFilter(private val tree: GrazieTreeComponent) : FilterComponent("GRAZIE_RULES_FILTER", 10) {
  private val expansionMonitor = TreeExpansionMonitor.install(tree)

  override fun filter() {
    expansionMonitor.freeze()

    filter(filter)

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

  private fun filter(filterString: String?) {
    if (filterString.isNullOrBlank()) {
      tree.resetTreeModel(LangTool.allRulesWithLangs())
      return
    }

    tree.resetTreeModel(
      LangTool.allRulesWithLangs().asSequence().map { (lang, categories) ->
        if (lang.nativeName.contains(filterString, true)) {
          lang to categories
        }
        else {
          lang to categories.map { (category, rules) ->
            if (category.category.name.contains(filterString, true)) {
              category to rules
            }
            else {
              category to TreeSet(rules.filter { it.rule.description.contains(filterString, true) })
            }
          }.toMap().filterValues { it.isNotEmpty() }
        }
      }.toMap().filterValues { it.isNotEmpty() }
    )
  }
}
