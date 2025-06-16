// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.ui.treeStructure.CachingTreePath
import com.intellij.ui.treeStructure.Tree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.Collections
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import kotlin.collections.ArrayDeque
import kotlin.collections.filter
import kotlin.collections.getValue
import kotlin.collections.hashMapOf
import kotlin.collections.listOf
import kotlin.collections.set
import kotlin.collections.toList

@RunInEdt
class TreeBaseConstructorSafetyTest {
  private val nodesByString = hashMapOf<String, DefaultMutableTreeNode>()
  private var sut: Tree? = null

  @AfterEach
  fun tearDown() {
    sut = null
    nodesByString.clear()
  }

  @Test
  fun `by default, a non-leaf root is expanded`() {
    sut = Tree(createModel("""
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

  @Test
  fun `if the root is collapsed in setModel, it remains collapsed`() {
    sut = object : Tree(createModel("""
      root
        node1
          leaf11
          leaf12
        leaf2
    """.trimIndent())) {
      override fun setModel(newModel: TreeModel?) {
        super.setModel(newModel)
        setExpandedState(path("root"), false)
      }
    }
    assertTreeStructure("""
      +root
    """.trimIndent())
  }

  @Test
  fun `calling setExpandedState from setModel is safe, and the updated state is kept`() {
    sut = object : Tree(createModel("""
      root
        node1
          leaf11
          leaf12
        leaf2
    """.trimIndent())) {
      override fun setModel(newModel: TreeModel?) {
        super.setModel(newModel)
        setExpandedState(path("root", "node1"), true)
      }
    }
    assertTreeStructure("""
      -root
        -node1
          leaf11
          leaf12
        leaf2
    """.trimIndent())
  }

  @Test
  fun `expanding and then collapsing the same path from setModel is safe, and the updated state is kept`() {
    sut = object : Tree(createModel("""
      root
        node1
          leaf11
          leaf12
        leaf2
    """.trimIndent())) {
      override fun setModel(newModel: TreeModel?) {
        super.setModel(newModel)
        setExpandedState(path("root", "node1"), true)
        setExpandedState(path("root", "node1"), false)
      }
    }
    assertTreeStructure("""
      -root
        +node1
        leaf2
    """.trimIndent())
  }

  @Test
  fun `calling expandPaths from setModel is safe, and the updated state is kept`() {
    sut = object : Tree(createModel("""
      root
        node1
          leaf11
          leaf12
        node2
          leaf21
          leaf22
    """.trimIndent())) {
      override fun setModel(newModel: TreeModel?) {
        super.setModel(newModel)
        expandPaths(listOf(path("root", "node1"), path("root", "node2")))
      }
    }
    assertTreeStructure("""
      -root
        -node1
          leaf11
          leaf12
        -node2
          leaf21
          leaf22
    """.trimIndent())
  }

  @Test
  fun `calling collapsePaths from setModel is safe, and the updated state is kept`() {
    sut = object : Tree(createModel("""
      root
        node1
          leaf11
          leaf12
        node2
          leaf21
          leaf22
        node3
          leaf31
          leaf32
    """.trimIndent())) {
      override fun setModel(newModel: TreeModel?) {
        super.setModel(newModel)
        expandPaths(listOf(path("root", "node1"), path("root", "node2"), path("root", "node3")))
        collapsePaths(listOf(path("root", "node1"), path("root", "node2")))
      }
    }
    assertTreeStructure("""
      -root
        +node1
        +node2
        -node3
          leaf31
          leaf32
    """.trimIndent())
  }

  @Test
  fun `calling getExpandedPaths from setModel is safe`() {
    sut = object : Tree(createModel("""
      root
        node1
          leaf11
          leaf12
    """.trimIndent())) {
      override fun setModel(newModel: TreeModel?) {
        super.setModel(newModel)
        expandPath(path("root", "node1"))
        assertThat(expandedPaths).containsExactlyInAnyOrder(path("root"), path("root", "node1"))
      }
    }
    assertTreeStructure("""
      -root
        -node1
          leaf11
          leaf12
    """.trimIndent())
  }

  @Test
  fun `calling getExpandedDescendants from setModel is safe`() {
    sut = object : Tree(createModel("""
      root
        node1
          leaf11
          leaf12
    """.trimIndent())) {
      override fun setModel(newModel: TreeModel?) {
        super.setModel(newModel)
        expandPath(path("root", "node1"))
        assertThat(getExpandedDescendants(path("root")).toList())
          .containsExactlyInAnyOrder(path("root"), path("root", "node1"))
      }
    }
    assertTreeStructure("""
      -root
        -node1
          leaf11
          leaf12
    """.trimIndent())
  }

  @Test
  fun `calling hasBeenExpanded from setModel is safe`() {
    sut = object : Tree(createModel("""
      root
        node1
          leaf11
          leaf12
    """.trimIndent())) {
      override fun setModel(newModel: TreeModel?) {
        super.setModel(newModel)
        expandPath(path("root", "node1"))
        collapsePath(path("root", "node1"))
        assertThat(hasBeenExpanded(path("root", "node1"))).isTrue()
      }
    }
    assertTreeStructure("""
      -root
        +node1
    """.trimIndent())
    val sut = checkNotNull(this.sut)
    assertThat(sut.hasBeenExpanded(path("root", "node1"))).isTrue()
  }

  @Test
  fun `calling isExpanded(TreePath) from setModel is safe`() {
    sut = object : Tree(createModel("""
      root
        node1
          leaf11
          leaf12
    """.trimIndent())) {
      override fun setModel(newModel: TreeModel?) {
        super.setModel(newModel)
        expandPath(path("root", "node1"))
        assertThat(isExpanded(path("root", "node1"))).isTrue()
      }
    }
    assertTreeStructure("""
      -root
        -node1
          leaf11
          leaf12
    """.trimIndent())
  }

  @Test
  fun `calling isExpanded(int) from setModel is safe`() {
    sut = object : Tree(createModel("""
      root
        node1
          leaf11
          leaf12
    """.trimIndent())) {
      init {
        isRootVisible = true
      }

      override fun setModel(newModel: TreeModel?) {
        super.setModel(newModel)
        expandPath(path("root", "node1"))
        assertThat(isExpanded(1)).isTrue()
      }
    }
    assertTreeStructure("""
      -root
        -node1
          leaf11
          leaf12
    """.trimIndent())
  }

  @Test
  fun `calling getDescendantToggledPaths from setModel is safe`() {
    sut = object : Tree(createModel("""
      root
        node1
          leaf11
          leaf12
    """.trimIndent())) {
      override fun setModel(newModel: TreeModel?) {
        super.setModel(newModel)
        expandPath(path("root", "node1"))
        assertThat(getDescendantToggledPaths(path("root")).toList())
          .containsExactlyInAnyOrder(path("root"), path("root", "node1"))
      }
    }
    assertTreeStructure("""
      -root
        -node1
          leaf11
          leaf12
    """.trimIndent())
  }

  @Test
  fun `calling removeDescendantToggledPaths from setModel is safe`() {
    sut = object : Tree(createModel("""
      root
        node1
          leaf11
          leaf12
    """.trimIndent())) {
      override fun setModel(newModel: TreeModel?) {
        super.setModel(newModel)
        expandPath(path("root", "node1"))
        removeDescendantToggledPaths(Collections.enumeration(listOf(path("root", "node1"))))
        assertThat(getDescendantToggledPaths(path("root")).toList()).containsOnly(path("root"))
      }
    }
    assertTreeStructure("""
      -root
        +node1
    """.trimIndent())
  }

  @Test
  fun `calling clearToggledPaths from setModel is safe`() {
    sut = object : Tree(createModel("""
      root
        node1
          leaf11
          leaf12
    """.trimIndent())) {
      override fun setModel(newModel: TreeModel?) {
        super.setModel(newModel)
        expandPath(path("root", "node1"))
        clearToggledPaths()
        assertThat(getDescendantToggledPaths(path("root")).toList()).isEmpty()
      }
    }
    assertTreeStructure("""
      +root
    """.trimIndent())
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

  private fun path(vararg elements: String): TreePath {
    var result: TreePath = CachingTreePath(nodesByString.getValue(elements[0]))
    for (i in 1 until elements.size) {
      result = result.pathByAddingChild(nodesByString.getValue(elements[i]))
    }
    return result
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
      val value = line.trim()
      val node = DefaultMutableTreeNode(value)
      nodesByString[value] = node
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
