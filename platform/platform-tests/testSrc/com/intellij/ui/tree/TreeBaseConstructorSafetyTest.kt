// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.ui.treeStructure.CachingTreePath
import com.intellij.ui.treeStructure.Tree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

class TreeBaseConstructorSafetyTest {
  private var sut: Tree? = null

  @AfterEach
  fun tearDown() {
    sut = null
  }

  @Test
  fun `by default, a non-leaf root is expanded`() {
    createRegularTree(createModel("""
      root
        node1
          leaf11
          leaf12
        leaf2
    """.trimIndent()))
    assertTreeStructure("""
      -root
        +node1
        leaf2
    """.trimIndent())
  }

  private fun createRegularTree(treeModel: TreeModel) {
    sut = Tree(treeModel)
  }

  private fun assertTreeStructure(structure: String) {
    val actualStructure = dumpTreeStructure()
    // these LFs make the output more readable
    assertThat("\n$actualStructure").isEqualTo("\n$structure\n")
  }

  private fun dumpTreeStructure(): String {
    val sut = checkNotNull(this.sut)
    val result = StringBuilder()
    val root = sut.model.root as DefaultMutableTreeNode?
    if (root != null) {
      dumpNodeStructure(CachingTreePath(root), 0, result)
    }
    return result.toString()
  }

  private fun dumpNodeStructure(path: TreePath, level: Int, result: StringBuilder) {
    val sut = checkNotNull(this.sut)
    val node = path.lastPathComponent as DefaultMutableTreeNode
    result.append(" ".repeat(level * 2))
    if (node.isLeaf) {
      result.append(node.userObject).append("\n")
    }
    else if (sut.isExpanded(path)) {
      result.append("-").append(node.userObject).append("\n")
      for (child in node.children().toList()) {
        dumpNodeStructure(path.pathByAddingChild(child), level + 1, result)
      }
    }
    else {
      result.append("+").append(node.userObject).append("\n")
    }
  }

  private fun createModel(content: String): TreeModel {
    val lines = content.split("\n").filter { it.isNotBlank() }
    val stack = ArrayDeque<DefaultMutableTreeNode>()
    var currentLevel = -1
    for (line in lines) {
      val indent = line.indexOfFirst { !it.isWhitespace() }
      assertThat(indent).isGreaterThanOrEqualTo(0)
      assertThat(indent % 2).isZero()
      val level = indent / 2
      val node = DefaultMutableTreeNode(line.trim())
      when {
        level == currentLevel + 1 -> {
          stack.lastOrNull()?.add(node)
          stack.addLast(node)
        }
        level <= currentLevel -> {
          repeat(currentLevel - level) { stack.removeLast() }
          stack.removeLast()
          stack.last().add(node)
          stack.addLast(node)
        }
        else -> {
          throw IllegalArgumentException("Unexpected tree structure: $content")
        }
      }
      currentLevel = level
    }
    return DefaultTreeModel(stack.firstOrNull())
  }
}
