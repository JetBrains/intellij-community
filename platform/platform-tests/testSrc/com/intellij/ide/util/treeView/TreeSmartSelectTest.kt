/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util.treeView

import com.intellij.testFramework.FlyIdeaTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.tree.TreeSmartSelectProvider
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import java.lang.IllegalArgumentException

import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * @author Konstantin Bulenkov
 */
class TreeSmartSelectTest : FlyIdeaTestCase() {
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
    TreeUtil.expandAll(myTree)
    return node
  }

  fun DefaultMutableTreeNode.select() {
    myTree.selectionPath = TreePath(myModel.getPathToRoot(this))
  }

  fun testSelectionIncrease() {
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
    myProvider.increaseSelection(myTree)
  }
  private fun decreaseSelection() {
    myProvider.decreaseSelection(myTree)
  }

  private fun assertTree(expected: String) {
    PlatformTestUtil.assertTreeEqual(myTree, expected, true)
  }

//  fun testSelectionDoesntJumpTooQuickly() {
//    val locations = myRoot.addChild("ktor")
//                            .addChild("ktor-core",
//                                      "ktor-features")
//                                 .addChild("jetty-http-client",
//                                           "ktor-locations")
//                          locations.addChild("src")
//                                     .addChild("asdsd.asdas.asdas")
//                                       .addChild("a",
//                                                 "b",
//                                                 "c").select()
//
//                          locations.addChild("tests")
//                                     .addChild("fooo")
//
//                          locations.addChild("zar.txt",
//                                             "zoo.txt")
//    TreeUtil.expand(myTree, 5)
//    increaseSelection()
//    increaseSelection()
//    increaseSelection()
//    increaseSelection()
//    increaseSelection()
//    assertTree("-/\n" +
//               " -ktor\n" +
//               "  ktor-core\n" +
//               "  -ktor-features\n" +
//               "   jetty-http-client\n" +
//               "   -[ktor-locations]\n" +
//               "    -[src]\n" +
//               "     -[asdsd.asdas.asdas]\n" +
//               "      [a]\n" +
//               "      [b]\n" +
//               "      [c]\n" +
//               "    -[tests]\n" +
//               "     [fooo]\n" +
//               "    [zar.txt]\n" +
//               "    [zoo.txt]\n")
//    }

    fun testDecreaseSimple() {
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

//  fun testIncDecFromNonLeaf() {
//    val com = myRoot.addChild("com")
//    val intellij = com.addChild("intellij")
//                intellij.addChild("a",
//                                  "b")
//                   com.addChild("x",
//                                "y")
//              myRoot.addChild("zzz")
//    intellij.select()
//    TreeUtil.expand(myTree, 3)
//
//    increaseSelection()
//    assertTree("-/\n" +
//               " -com\n" +
//               "  -[intellij]\n" +
//               "   [a]\n" +
//               "   [b]\n" +
//               "  [x]\n" +
//               "  [y]\n" +
//               " zzz\n")
//
//    decreaseSelection()
//    decreaseSelection()
//    assertTree("-/\n" +
//               " -com\n" +
//               "  -[intellij]\n" +
//               "   a\n" +
//               "   b\n" +
//               "  x\n" +
//               "  y\n" +
//               " zzz\n")
//  }
}

