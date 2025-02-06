package com.intellij.driver.sdk.ui.components.idea

import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.tree
import kotlin.time.Duration.Companion.seconds

fun ProjectStructureUI.modulesTree(): ModulesTree {
  return ModulesTree(tree("(//div[contains(@classhierarchy, 'javax.swing.JTree')])[1]"))
}

class ModulesTree(private val tree: JTreeUiComponent) {

  fun selectModule(moduleName: String) {
    tree.clickPath(*moduleName.split(".").toTypedArray())
  }

  fun forEachModule(action: () -> Unit) {
    tree.expandAll(10.seconds)
    val size = tree.collectExpandedPaths().size
    for (i in 0 until size) {
      tree.clickRow(i)
      action()
    }
  }
}