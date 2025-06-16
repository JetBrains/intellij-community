package com.intellij.driver.sdk.ui.components.idea

import com.intellij.driver.model.TreePath
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.tree
import kotlin.time.Duration.Companion.seconds

fun ProjectStructureUI.modulesTree(): ModulesTree {
  return ModulesTree(tree("(//div[contains(@classhierarchy, 'javax.swing.JTree')])[1]"))
}

class ModulesTree(private val tree: JTreeUiComponent) {
  val selectedPaths: List<TreePath>
    get() = tree.collectSelectedPaths()

  fun expandByPath(path: String, fullMatch: Boolean = true) {
    val segments = path.split(".",).filter { it.isNotEmpty() }.toTypedArray()
    tree.expandPath(
      *segments,
      fullMatch = fullMatch
    )
  }

  fun selectModule(moduleName: String, fullMatch: Boolean = true) {
    val segments = moduleName.split(".",).filter { it.isNotEmpty() }.toTypedArray()
      .takeIf { it.isNotEmpty() } ?: return

    tree.waitOneText(segments.first())
    tree.clickPath(
      *segments,
      fullMatch = fullMatch
    )
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