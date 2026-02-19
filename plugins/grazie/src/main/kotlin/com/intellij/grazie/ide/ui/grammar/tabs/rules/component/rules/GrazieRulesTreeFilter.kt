// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.grammar.tabs.rules.component.rules

import ai.grazie.nlp.langs.Language
import com.intellij.grazie.detection.toAvailableLang
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.GrazieTreeComponent
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.allRules
import com.intellij.packageDependencies.ui.TreeExpansionMonitor
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.tree.DefaultTreeModel

internal class GrazieRulesTreeFilter(
  private val tree: GrazieTreeComponent,
  private val language: Language,
) {
  private val expansionMonitor = TreeExpansionMonitor.install(tree)

  fun filter(filterText: String?) {
    val hadSelection = !tree.selectionPaths.isNullOrEmpty()
    expansionMonitor.freeze()

    filterTree(filterText)

    (tree.model as DefaultTreeModel).reload()

    if (filterText.isNullOrBlank()) {
      TreeUtil.collapseAll(tree, 0)
      if (hadSelection) {
        expansionMonitor.restore()
      }
      else {
        expansionMonitor.unfreeze()
      }
    }
    else {
      TreeUtil.expandAll(tree)
      expansionMonitor.unfreeze()
    }
  }

  private fun filterTree(filterString: String?) {
    val lang = language.toAvailableLang()
    if (filterString.isNullOrBlank()) {
      tree.resetTreeModel(allRules(lang))
      return
    }

    val rules = allRules(lang)
      .filter {
        lang.nativeName.contains(filterString, true) ||
        it.categories.any { cat -> cat.contains(filterString, true) } ||
        it.presentableName.contains(filterString, true) ||
        it.searchableDescription.contains(filterString, true)
      }
    tree.resetTreeModel(rules)
  }
}
