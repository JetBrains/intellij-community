// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.ui.tree.ui.DefaultTreeLayoutCache
import org.easymock.EasyMock.mock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Rectangle
import javax.swing.tree.*
import javax.swing.tree.AbstractLayoutCache.NodeDimensions

private const val defaultRowHeight = 22
private const val indent = 5

class DefaultTreeLayoutCacheTest {

  private lateinit var model: DefaultTreeModel
  private lateinit var selectionModel: TreeSelectionModel
  private lateinit var sut: DefaultTreeLayoutCache

  @BeforeEach
  fun setUp() {
    selectionModel = mock(TreeSelectionModel::class.java)
    model = DefaultTreeModel(null)
    sut = DefaultTreeLayoutCache(defaultRowHeight) { }
    sut.nodeDimensions = NodeDimensionsImpl(emptyMap())
    sut.selectionModel = selectionModel
  }

  @Test
  fun `initial state - empty tree`() {
    testStructure(
      initOps = { },
      assertions = { assertStructure("") },
    )
  }

  @Test
  fun `initial state - just root`() {
    testStructure(
      initOps = {
        setModelStructure("r")
        sut.isRootVisible = true
      },
      assertions = { assertStructure("r") },
    )
  }

  @Test
  fun `initial state - root expanded by default`() {
    testStructure(
      initOps = {
        setModelStructure("""
          |r
          | a1
        """.trimMargin())
        sut.isRootVisible = true
      },
      assertions = {
        assertStructure("""
        |r
        | a1
      """.trimMargin())
      },
    )
  }

  @Test
  fun `initial state - invisible root always expanded`() {
    testStructure(
      initOps = {
        setModelStructure("""
          |r
          | a1
        """.trimMargin())
        sut.isRootVisible = false
      },
      assertions = { assertStructure("""
        | a1
      """.trimMargin()) },
    )
  }

  @Test
  fun `collapse visible root`() {
    testStructure(
      initOps = {
        setModelStructure("""
          |r
          | a1
        """.trimMargin())
        sut.isRootVisible = true
      },
      modOps = {
        sut.setExpandedState(path("r"), false)
      },
      assertions = { assertStructure("r".trimMargin()) },
    )
  }

  @Test
  fun `collapse invisible root`() {
    testStructure(
      initOps = {
        setModelStructure("""
          |r
          | a1
        """.trimMargin())
        sut.isRootVisible = false
      },
      modOps = {
        sut.setExpandedState(path("r"), false)
      },
      assertions = { assertStructure("".trimMargin()) },
    )
  }

  @Test
  fun `expand visible root and its children`() {
    testStructure(
      initOps = {
        setModelStructure("""
          |r
          | a1
          |  b11
          | a2
          |  b21
          """.trimMargin()
        )
        sut.isRootVisible = true
      },
      modOps = {
        sut.setExpandedState(path("r"), true)
        sut.setExpandedState(path("r/a1"), true)
        sut.setExpandedState(path("r/a2"), true)
      },
      assertions = {
        assertStructure("""
          |r
          | a1
          |  b11
          | a2
          |  b21
          """.trimMargin()
        )
     },
    )
  }

  @Test
  fun `re-expand visible root with previously expanded children`() {
    testStructure(
      initOps = {
        setModelStructure("""
          |r
          | a1
          |  b11
          | a2
          |  b21
          """.trimMargin()
        )
        sut.isRootVisible = true
      },
      modOps = {
        sut.setExpandedState(path("r"), true)
        sut.setExpandedState(path("r/a1"), true)
        sut.setExpandedState(path("r/a2"), true)
        sut.setExpandedState(path("r"), false)
        sut.setExpandedState(path("r"), true)
      },
      assertions = {
        assertStructure("""
          |r
          | a1
          |  b11
          | a2
          |  b21
          """.trimMargin()
        )
      },
    )
  }

  private fun testStructure(
    initOps: () -> Unit = { },
    modOps: () -> Unit = { },
    assertions: () -> Unit = { },
  ) {
    initOps()
    sut.model = model
    modOps()
    assertions()
  }

  private fun setModelStructure(s: String) {
    val rows = s.split("\n")
    val stack = ArrayDeque<Node>()
    var level = -1
    for (row in rows) {
      val rowLevel = row.indexOfFirst { !it.isWhitespace() }
      val node = Node(row.substring(rowLevel until row.length))
      if (rowLevel > level) {
        assertThat(rowLevel).isEqualTo(level + 1) // can't have a child deeper than one level below
        val parent = stack.lastOrNull()
        parent?.insert(node, parent.childCount)
        stack.addLast(node)
        level = rowLevel
      }
      else {
        while (level >= rowLevel) {
          stack.removeLast()
          --level
        }
        val parent = stack.last()
        parent.insert(node, parent.childCount)
        stack.addLast(node)
        ++level
      }
    }
    val root = stack.firstOrNull()
    if (root != null) {
      model.setRoot(root)
    }
  }

  private fun path(s: String): TreePath = TreePathUtil.convertCollectionToTreePath(
    s.split('/').map { Node(it) }
  )

  private fun assertStructure(structureString: String) {
    val actualStructure = (0 until sut.rowCount)
      .map { i -> sut.getPathForRow(i)?.let { path ->
          " ".repeat(path.pathCount - 1) + path.lastPathComponent.toString()
        }
      }.joinToString("\n")
    assertThat(actualStructure).isEqualTo(structureString)
  }

  private class NodeDimensionsImpl(private val heights: Map<String, Int>) : NodeDimensions() {
    override fun getNodeDimensions(value: Any?, row: Int, depth: Int, expanded: Boolean, bounds: Rectangle?): Rectangle =
      (bounds ?: Rectangle()).apply {
        x = depth * indent
        value as String
        width = value.length
        height = heights[value] ?: defaultRowHeight
      }
  }

  private class Node(userObject: String) : DefaultMutableTreeNode(userObject) {

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Node

      return userObject == other.userObject
    }

    override fun hashCode() = userObject.hashCode()

  }

}
