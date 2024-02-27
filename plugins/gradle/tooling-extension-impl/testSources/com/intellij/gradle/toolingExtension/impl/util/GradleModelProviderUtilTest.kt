// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util

import com.intellij.platform.testFramework.treeAssertion.SimpleTree
import com.intellij.platform.testFramework.treeAssertion.buildTree
import com.intellij.platform.testFramework.treeAssertion.getTreeString
import org.junit.Test
import org.junit.jupiter.api.Assertions

class GradleModelProviderUtilTest {

  @Test
  fun `test forward tree traverser`() {
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
    GradleModelProviderUtil.traverseTree(tree.roots.first(), SimpleTree.Node<Int>::children) {
      result.add(it.value)
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
    GradleModelProviderUtil.backwardTraverseTree(tree.roots.first(), SimpleTree.Node<Int>::children) {
      result.add(it.value)
    }
    Assertions.assertEquals(listOf(3, 5, 6, 7, 8, 4, 2, 10, 12, 11, 13, 9, 1), result) {
      "Incorrect traversing order for the tree:\n" + tree.getTreeString()
    }
  }
}