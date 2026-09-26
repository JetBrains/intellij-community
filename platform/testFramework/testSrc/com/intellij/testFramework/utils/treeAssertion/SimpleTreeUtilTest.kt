// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.treeAssertion

import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.platform.testFramework.assertion.treeAssertion.allNodes
import com.intellij.platform.testFramework.assertion.treeAssertion.buildTree
import com.intellij.platform.testFramework.assertion.treeAssertion.toMutableTree
import com.intellij.platform.testFramework.assertion.treeAssertion.getTreeString
import com.intellij.platform.testFramework.assertion.treeAssertion.mapTreeValues
import com.intellij.platform.testFramework.assertion.treeAssertion.node
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SimpleTreeUtilTest {

  @Test
  fun `test SimpleTreeUtil#toMutableTree`() {
    val tree = buildTree {
      root("1", 1) {
        node("1.1", 2) {
          node("1.1.1", 3)
          node("1.1.2", 4) {
            node("1.1.2.1", 5)
            node("1.1.2.2", 6)
            node("1.1.2.3", 7)
            node("1.1.2.4", 8)
          }
        }
        node("1.2", 9) {
          node("1.2.1", 10)
          node("1.2.2", 11) {
            node("1.2.2.1", 12)
          }
          node("1.2.3", 13)
        }
      }
    }

    val treeCopy1 = tree.toMutableTree()
    val treeCopy2 = tree.toMutableTree()

    SimpleTreeAssertion.assertTreeEquals(tree, treeCopy1)
    SimpleTreeAssertion.assertTreeEquals(tree, treeCopy2)
    SimpleTreeAssertion.assertTreeEquals(treeCopy1, treeCopy2)

    val node1 = treeCopy1.roots[0].children[0].children[1]
    Assertions.assertEquals("1.1.2", node1.name)
    Assertions.assertEquals(4, node1.value)
    node1.value = 100

    Assertions.assertThrows(AssertionError::class.java) {
      SimpleTreeAssertion.assertTreeEquals(tree, treeCopy1)
    }
    SimpleTreeAssertion.assertTreeEquals(tree, treeCopy2)
    Assertions.assertThrows(AssertionError::class.java) {
      SimpleTreeAssertion.assertTreeEquals(treeCopy1, treeCopy2)
    }
  }

  @Test
  fun `test SimpleTreeUtil#getTreeString`() {
    val expectedTreeString = """
      |-1
      | -1.1
      |  1.1.1
      |  -1.1.2
      |   1.1.2.1
      |   1.1.2.2
      |   1.1.2.3
      |   1.1.2.4
      | -1.2
      |  1.2.1
      |  -1.2.2
      |   1.2.2.1
      |  1.2.3
    """.trimMargin()

    val actualTreeString = buildTree {
      root("1", null) {
        node("1.1", null) {
          node("1.1.1", null)
          node("1.1.2", null) {
            node("1.1.2.1", null)
            node("1.1.2.2", null)
            node("1.1.2.3", null)
            node("1.1.2.4", null)
          }
        }
        node("1.2", null) {
          node("1.2.1", null)
          node("1.2.2", null) {
            node("1.2.2.1", null)
          }
          node("1.2.3", null)
        }
      }
    }.getTreeString()

    Assertions.assertEquals(expectedTreeString, actualTreeString)
  }

  @Test
  fun `test SimpleTreeUtil#buildTree`() {
    val expectedTree = buildTree {
      root("1", null) {
        node("1.1", null) {
          node("1.1.1", null)
          node("1.1.2", null) {
            node("1.1.2.1", null)
            node("1.1.2.2", null)
            node("1.1.2.3", null)
            node("1.1.2.4", null)
          }
        }
        node("1.2", null) {
          node("1.2.1", null)
          node("1.2.2", null) {
            node("1.2.2.1", null)
          }
          node("1.2.3", null)
        }
      }
    }

    val actualTree = buildTree("""
      |-1
      | -1.1
      |  1.1.1
      |  -1.1.2
      |   1.1.2.1
      |   1.1.2.2
      |   1.1.2.3
      |   1.1.2.4
      | -1.2
      |  1.2.1
      |  -1.2.2
      |   1.2.2.1
      |  1.2.3
    """.trimMargin())

    SimpleTreeAssertion.assertTreeEquals(expectedTree, actualTree)
  }

  @Test
  fun `test SimpleTreeUtil#mapTreeValues`() {
    val expectedTree = buildTree<Int> {
      root("1", 1) {
        node("1.1", 0) {
          node("1.1.1", 3)
          node("1.1.2", 0) {
            node("1.1.2.1", 5)
            node("1.1.2.2", 0)
            node("1.1.2.3", 7)
            node("1.1.2.4", 0)
          }
        }
        node("1.2", 9) {
          node("1.2.1", 0)
          node("1.2.2", 11) {
            node("1.2.2.1", 0)
          }
          node("1.2.3", 13)
        }
      }
    }

    val actualTree = buildTree<Int> {
      root("1", 1) {
        node("1.1", 2) {
          node("1.1.1", 3)
          node("1.1.2", 4) {
            node("1.1.2.1", 5)
            node("1.1.2.2", 6)
            node("1.1.2.3", 7)
            node("1.1.2.4", 8)
          }
        }
        node("1.2", 9) {
          node("1.2.1", 10)
          node("1.2.2", 11) {
            node("1.2.2.1", 12)
          }
          node("1.2.3", 13)
        }
      }
    }.mapTreeValues {
      if (it.value % 2 == 0) 0 else it.value
    }

    SimpleTreeAssertion.assertTreeEquals(expectedTree, actualTree)
  }

  @Nested
  inner class AssertNodeTest {

    @Test
    fun `finds node by name and calls assert`() {
      val tree = buildTree {
        root("root", 0) {
          node("child", 42)
        }
      }
      Assertions.assertEquals(42, tree.node("child").value)
    }

    @Test
    fun `fails when node with name is not found`() {
      val tree = buildTree {
        root("root", 0)
      }
      Assertions.assertThrows(AssertionError::class.java) {
        tree.node("nonexistent")
      }
    }

    @Test
    fun `fails when several nodes with name are found`() {
      val tree = buildTree {
        root("root", 0) {
          node("child", 21)
          node("child", 42)
        }
      }
      Assertions.assertThrows(AssertionError::class.java) {
        tree.node("child")
      }
    }

    @Test
    fun `finds node by regex and calls assert`() {
      val tree = buildTree {
        root("root", 0) {
          node("child-123", 42)
        }
      }
      Assertions.assertEquals(42, tree.node("child-\\d+".toRegex()).value)
    }

    @Test
    fun `fails when node with regex is not found`() {
      val tree = buildTree {
        root("root", 0)
      }
      Assertions.assertThrows(AssertionError::class.java) {
        tree.node("nonexistent".toRegex())
      }
    }
    @Test
    fun `fails when several nodes with regex are found`() {
      val tree = buildTree {
        root("root", 0) {
          node("child", 21)
          node("child", 42)
        }
      }
      Assertions.assertThrows(AssertionError::class.java) {
        tree.node("child".toRegex())
      }
    }

    @Test
    fun `propagates assertion failure from lambda`() {
      val tree = buildTree {
        root("root", 0) {
          node("child", 42)
        }
      }
      Assertions.assertThrows(AssertionError::class.java) {
        Assertions.assertEquals(99, tree.node("child").value)
      }
    }

    @Test
    fun `finds deeply nested node`() {
      val tree = buildTree {
        root("root", 0) {
          node("level1", 1) {
            node("level2", 2) {
              node("deep", 100)
            }
          }
        }
      }
      Assertions.assertEquals(100, tree.node("deep").value)
    }
  }

  @Nested
  inner class AllNodesTest {

    @Test
    fun `empty tree returns empty sequence`() {
      val tree = buildTree<Int> {}
      Assertions.assertEquals(emptyList<Any>(), tree.allNodes().toList())
    }

    @Test
    fun `single root returns that node`() {
      val tree = buildTree {
        root("root", 42)
      }
      val nodes = tree.allNodes().toList()
      Assertions.assertEquals(1, nodes.size)
      Assertions.assertEquals("root", nodes[0].name)
      Assertions.assertEquals(42, nodes[0].value)
    }

    @Test
    fun `returns all nodes`() {
      val tree = buildTree {
        root("1", 1) {
          node("1.1", 2) {
            node("1.1.1", 3)
            node("1.1.2", 4)
          }
          node("1.2", 5)
        }
      }
      val nodes = tree.allNodes().toList()
      Assertions.assertEquals(5, nodes.size)
      Assertions.assertEquals(setOf("1", "1.1", "1.1.1", "1.1.2", "1.2"), nodes.map { it.name }.toSet())
    }

    @Test
    fun `traversal order is right-to-left DFS`() {
      val tree = buildTree {
        root("1", Unit) {
          node("1.1", Unit) {
            node("1.1.1", Unit)
            node("1.1.2", Unit)
          }
          node("1.2", Unit)
        }
      }
      Assertions.assertEquals(
        listOf("1", "1.2", "1.1", "1.1.2", "1.1.1"),
        tree.allNodes().map { it.name }.toList(),
      )
    }
  }
}