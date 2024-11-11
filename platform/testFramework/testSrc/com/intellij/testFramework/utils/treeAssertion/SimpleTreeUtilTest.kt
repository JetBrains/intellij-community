// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.treeAssertion

import com.intellij.platform.testFramework.treeAssertion.SimpleTreeAssertion
import com.intellij.platform.testFramework.treeAssertion.buildTree
import com.intellij.platform.testFramework.treeAssertion.deepCopyTree
import com.intellij.platform.testFramework.treeAssertion.getTreeString
import com.intellij.platform.testFramework.treeAssertion.mapTreeValues
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SimpleTreeUtilTest {

  @Test
  fun `test SimpleTreeUtil#deepCopyTree`() {
    val tree = buildTree<Int> {
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

    val treeCopy1 = tree.deepCopyTree()
    val treeCopy2 = tree.deepCopyTree()

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
}