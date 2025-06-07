// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util

import com.intellij.platform.testFramework.assertion.treeAssertion.buildTree
import com.intellij.platform.testFramework.assertion.treeAssertion.getTreeString
import org.junit.Test
import org.junit.jupiter.api.Assertions

class GradleTreeTraverserUtilTest {

  @Test
  fun `test depth first tree traverser`() {
    val tree = buildTree<Int> {
      root("1", 1) {
        node("1.1", 2) {
          node("1.1.1", 3)
          node("1.1.2", 4) {
            node("1.1.2.1", 5)
            node("1.1.2.1", 6)
            node("1.1.2.1", 7)
            node("1.1.2.1", 8)
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

    val result = ArrayList<Int>()
    GradleTreeTraverserUtil.depthFirstTraverseTree(tree.roots.first()) {
      result.add(it.value)
      it.children
    }
    Assertions.assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13), result) {
      "Incorrect traversing order for the tree:\n" + tree.getTreeString()
    }
  }

  @Test
  fun `test breadth first tree traverser`() {
    val tree = buildTree<Int> {
      root("1", 1) {
        node("1.1", 2) {
          node("1.1.1", 3)
          node("1.1.2", 4) {
            node("1.1.2.1", 5)
            node("1.1.2.1", 6)
            node("1.1.2.1", 7)
            node("1.1.2.1", 8)
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

    val result = ArrayList<Int>()
    GradleTreeTraverserUtil.breadthFirstTraverseTree(tree.roots.first()) {
      result.add(it.value)
      it.children
    }
    Assertions.assertEquals(listOf(1, 2, 9, 3, 4, 10, 11, 13, 5, 6, 7, 8, 12), result) {
      "Incorrect traversing order for the tree:\n" + tree.getTreeString()
    }
  }

  @Test
  fun `test backward tree traverser`() {
    val tree = buildTree<Int> {
      root("1", 1) {
        node("1.1", 2) {
          node("1.1.1", 3)
          node("1.1.2", 4) {
            node("1.1.2.1", 5)
            node("1.1.2.1", 6)
            node("1.1.2.1", 7)
            node("1.1.2.1", 8)
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

    val result = ArrayList<Int>()
    GradleTreeTraverserUtil.backwardTraverseTree(tree.roots.first(), { it.children }) {
      result.add(it.value)
    }
    Assertions.assertEquals(listOf(3, 5, 6, 7, 8, 4, 2, 10, 12, 11, 13, 9, 1), result) {
      "Incorrect traversing order for the tree:\n" + tree.getTreeString()
    }
  }

  @Test
  fun `test depth first tree traverser with path`() {
    val tree = buildTree<Int> {
      root("1", 1) {
        node("1.1", 2) {
          node("1.1.1", 3)
          node("1.1.2", 4) {
            node("1.1.2.1", 5)
            node("1.1.2.1", 6)
            node("1.1.2.1", 7)
            node("1.1.2.1", 8)
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

    val resultWithPath = HashMap<Int, List<Int>>()
    val result = ArrayList<Int>()
    GradleTreeTraverserUtil.depthFirstTraverseTreeWithPath(tree.roots.first()) { path, it ->
      resultWithPath[it.value] = path.map { it.value }
      result.add(it.value)
      it.children
    }
    Assertions.assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13), result) {
      "Incorrect traversing order for the tree:\n" + tree.getTreeString()
    }
    Assertions.assertEquals(mapOf(
      1 to listOf(),
      2 to listOf(1),
      3 to listOf(1, 2),
      4 to listOf(1, 2),
      5 to listOf(1, 2, 4),
      6 to listOf(1, 2, 4),
      7 to listOf(1, 2, 4),
      8 to listOf(1, 2, 4),
      9 to listOf(1),
      10 to listOf(1, 9),
      11 to listOf(1, 9),
      12 to listOf(1, 9, 11),
      13 to listOf(1, 9)
    ), resultWithPath) {
      "Incorrect traversing path during node children evaluation:\n" + tree.getTreeString()
    }
  }
}