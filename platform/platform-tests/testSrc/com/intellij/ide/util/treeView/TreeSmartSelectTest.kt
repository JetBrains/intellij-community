// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.tree.TreeSmartSelectProvider
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import org.junit.Test
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * @author Konstantin Bulenkov
 */
class TreeSmartSelectTest : BareTestFixtureTestCase() {
  private var myProvider = TreeSmartSelectProvider()
  internal var myRoot = node("/")
  internal var myModel = DefaultTreeModel(myRoot)
  internal var myTree = Tree(myModel)

  private fun node() = DefaultMutableTreeNode(null)

  private fun node(name: String) = DefaultMutableTreeNode(name)

  fun DefaultMutableTreeNode.addChild(vararg names: String): DefaultMutableTreeNode {
    if (names.isEmpty()) throw IllegalArgumentException()

    var node = node()
    for (name in names) {
      node = node(name)
      add(node)
    }
    // the following method expands nodes on EDT,
    // so in this case we should pause the main thread
    TreeUtil.promiseExpandAll(myTree).blockingGet(10000)
    return node
  }

  fun DefaultMutableTreeNode.select() {
    runInEdtAndWait {
      myTree.selectionPath = TreePath(myModel.getPathToRoot(this))
    }
  }

  @Test fun testSelectionIncrease() {
    myRoot.addChild("com")
            .addChild("intellij")
              .addChild("a",
                        "b",
                        "c").select()

    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   a\n" +
               "   b\n" +
               "   [c]\n")

    increaseSelection()
    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   [a]\n" +
               "   [b]\n" +
               "   [c]\n")

    increaseSelection()
    assertTree("-/\n" +
               " -com\n" +
               "  -[intellij]\n" +
               "   [a]\n" +
               "   [b]\n" +
               "   [c]\n")

    increaseSelection()
    assertTree("-/\n" +
               " -[com]\n" +
               "  -[intellij]\n" +
               "   [a]\n" +
               "   [b]\n" +
               "   [c]\n")

    increaseSelection()
    assertTree("-[/]\n" +
               " -[com]\n" +
               "  -[intellij]\n" +
               "   [a]\n" +
               "   [b]\n" +
               "   [c]\n")

    increaseSelection()
    assertTree("-[/]\n" +
               " -[com]\n" +
               "  -[intellij]\n" +
               "   [a]\n" +
               "   [b]\n" +
               "   [c]\n")
  }

  private fun increaseSelection() {
    runInEdtAndWait {
      myProvider.increaseSelection(myTree)
    }
  }

  private fun decreaseSelection() {
    runInEdtAndWait {
      myProvider.decreaseSelection(myTree)
    }
  }

  private fun assertTree(expected: String) {
    PlatformTestUtil.assertTreeEqual(myTree, expected, true)
  }

  @Test fun testDecreaseSimple() {
    myRoot.addChild("com")
      .addChild("a", "b", "c").select()

    increaseSelection()
    increaseSelection()
    decreaseSelection()
    assertTree("-/\n" +
               " -com\n" +
               "  [a]\n" +
               "  [b]\n" +
               "  [c]\n")
    }
}
