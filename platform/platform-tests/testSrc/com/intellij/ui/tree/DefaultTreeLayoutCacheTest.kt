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

  private lateinit var root: Node
  private lateinit var a1: Node
  private lateinit var a2: Node
  private lateinit var b11: Node
  private lateinit var b21: Node
  private lateinit var model: DefaultTreeModel
  private lateinit var selectionModel: TreeSelectionModel
  private lateinit var sut: DefaultTreeLayoutCache

  @BeforeEach
  fun setUp() {
    selectionModel = mock(TreeSelectionModel::class.java)
    root = Node("r")
    a1 = Node("a1")
    a2 = Node("a2")
    b11 = Node("b11")
    b21 = Node("b21")
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
        model.setRoot(root)
        sut.isRootVisible = true
      },
      assertions = { assertStructure("r") },
    )
  }

  @Test
  fun `initial state - root expanded by default`() {
    testStructure(
      initOps = {
        model.setRoot(root)
        root.insert(a1, 0)
        sut.isRootVisible = true
      },
      assertions = { assertStructure("""
        |r
        | a1
      """.trimMargin()) },
    )
  }

  @Test
  fun `initial state - invisible root always expanded`() {
    testStructure(
      initOps = {
        model.setRoot(root)
        root.insert(a1, 0)
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
        model.setRoot(root)
        root.insert(a1, 0)
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
        model.setRoot(root)
        root.insert(a1, 0)
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
        model.setRoot(root)
        root.insert(a1, 0)
        a1.insert(b11, 0)
        root.insert(a2, 1)
        a2.insert(b21, 0)
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
