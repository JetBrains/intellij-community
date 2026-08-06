// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.treeAssertion

import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.platform.testFramework.assertion.treeAssertion.buildTreePathTree
import com.intellij.platform.testFramework.assertion.treeAssertion.userObject
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

@TestApplication
class SimpleJTreeUtilTest {

  @Test
  fun `test buildTreePathTree single root`() {
    val tree = JTree(DefaultTreeModel(
      DefaultMutableTreeNode("root")
    ))

    val actualTree = buildTreePathTree(tree)

    SimpleTreeAssertion.assertTree(actualTree) {
      assertNode("root")
    }
  }

  @Test
  fun `test buildTreePathTree flat structure`() {
    val tree = JTree(DefaultTreeModel(
      DefaultMutableTreeNode("parent").apply {
        add(DefaultMutableTreeNode("child1"))
        add(DefaultMutableTreeNode("child2"))
        add(DefaultMutableTreeNode("child3"))
      }
    ))

    val actualTree = buildTreePathTree(tree)

    SimpleTreeAssertion.assertTree(actualTree) {
      assertNode("parent") {
        assertNode("child1")
        assertNode("child2")
        assertNode("child3")
      }
    }
  }

  @Test
  fun `test buildTreePathTree deep hierarchy`() {
    val tree = JTree(DefaultTreeModel(
      DefaultMutableTreeNode("root").apply {
        add(DefaultMutableTreeNode("middle1").apply {
          add(DefaultMutableTreeNode("leaf1"))
          add(DefaultMutableTreeNode("leaf2"))
        })
        add(DefaultMutableTreeNode("middle2").apply {
          add(DefaultMutableTreeNode("leaf3"))
        })
      }
    ))

    val actualTree = buildTreePathTree(tree)

    SimpleTreeAssertion.assertTree(actualTree) {
      assertNode("root") {
        assertNode("middle1") {
          assertNode("leaf1")
          assertNode("leaf2")
        }
        assertNode("middle2") {
          assertNode("leaf3")
        }
      }
    }
  }

  @Test
  fun `test buildTreePathTree node name derived from user object toString`() {
    val userObject = object {
      override fun toString(): String = "custom-name"
    }
    val tree = JTree(DefaultTreeModel(
      DefaultMutableTreeNode(userObject)
    ))

    val actualTree = buildTreePathTree(tree)

    SimpleTreeAssertion.assertTree(actualTree) {
      assertNode("custom-name")
    }
  }

  @Test
  fun `test buildTreePathTree preserves user objects as values`() {
    val rootUserObject = "root-value"
    val childUserObject = "child-value"
    val tree = JTree(DefaultTreeModel(
      DefaultMutableTreeNode(rootUserObject).apply {
        add(DefaultMutableTreeNode(childUserObject))
      }
    ))

    val actualTree = buildTreePathTree(tree)

    SimpleTreeAssertion.assertTree(actualTree) {
      assertNode("root-value") {
        assertValue { assertSame(rootUserObject, it.userObject) }
        assertNode("child-value") {
          assertValue { assertSame(childUserObject, it.userObject) }
        }
      }
    }
  }

  @Test
  fun `test buildTreePathTree null user object`() {
    val tree = JTree(DefaultTreeModel(
      DefaultMutableTreeNode(null)
    ))

    val actualTree = buildTreePathTree(tree)

    SimpleTreeAssertion.assertTree(actualTree) {
      assertNode("null") {
        assertValue { assertEquals(null, it.userObject) }
      }
    }
  }
}
