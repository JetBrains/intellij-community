// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.ui.tree.ui.DefaultTreeLayoutCache
import org.easymock.EasyMock.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Rectangle
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
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
    selectionModel = strictMock(TreeSelectionModel::class.java)
    model = DefaultTreeModel(null)
    sut = DefaultTreeLayoutCache(defaultRowHeight) { }
    sut.nodeDimensions = NodeDimensionsImpl(emptyMap())
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
  fun `initial state - just invisible root`() {
    testStructure(
      initOps = {
        setModelStructure("r")
        sut.isRootVisible = false
      },
      assertions = { assertStructure("") },
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
  fun `make root visible`() {
    testStructure(
      initOps = {
        setModelStructure("""
          |r
          | a1
        """.trimMargin())
        selectionModel.expect { resetRowSelection() }
      },
      modOps = {
        sut.isRootVisible = true
      },
      assertions = {
        assertStructure("""
          |r
          | a1""".trimMargin()
        )
      },
    )
  }

  @Test
  fun `make root invisible`() {
    testStructure(
      initOps = {
        setModelStructure("""
          |r
          | a1
        """.trimMargin())
        sut.isRootVisible = true
        selectionModel.expect {
          removeSelectionPath(path("r"))
          resetRowSelection()
        }
      },
      modOps = {
        sut.isRootVisible = false
      },
      assertions = {
        assertStructure(" a1")
      },
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
        selectionModel.expect { resetRowSelection() }
      },
      modOps = {
        sut.setExpandedState("r", false)
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
        selectionModel.expect { resetRowSelection() }
      },
      modOps = {
        sut.setExpandedState("r", false)
      },
      assertions = { assertStructure("".trimMargin()) },
    )
  }

  @Test
  fun `expand visible root`() {
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
        sut.setExpandedState("r", true)
      },
      assertions = {
        assertStructure("""
          |r
          | a1
          | a2
          """.trimMargin()
        )
     },
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
        selectionModel.expect { repeat(2) { resetRowSelection() } }
      },
      modOps = {
        sut.setExpandedState("r", true)
        sut.setExpandedState("r/a1", true)
        sut.setExpandedState("r/a2", true)
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
        selectionModel.expect {
          repeat(4) { resetRowSelection() } // 4 times because the root is initially expanded, so the 1st expand is a no-op
        }
      },
      modOps = {
        sut.setExpandedState("r", true)
        sut.setExpandedState("r/a1", true)
        sut.setExpandedState("r/a2", true)
        sut.setExpandedState("r", false)
        sut.setExpandedState("r", true)
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
  fun `insert the only node and expand`() {
    testStructure(
      initOps = {
        setModelStructure("""
          |r
          | a1
          """.trimMargin()
        )
        sut.isRootVisible = true
        selectionModel.expect { resetRowSelection() }
      },
      modOps = {
        node("r/a1").insert("b11", 0)
        sut.setExpandedState("r/a1", true)
      },
      assertions = {
        assertStructure("""
          |r
          | a1
          |  b11
          """.trimMargin()
        )
      },
    )
  }

  @Test
  fun `replace one node with multiple nodes`() {
    testStructure(
      initOps = {
        setModelStructure("""
          |r
          | a1
          |  b1
          """.trimMargin()
        )
        sut.isRootVisible = true
        selectionModel.expect { repeat(3) { resetRowSelection() } }
      },
      modOps = {
        sut.setExpandedState("r/a1", true)
        node("r/a1").setLeaf(false) // to avoid auto-collapsing
        node("r/a1/b1").remove()
        node("r/a1").insert(0 to "b11", 1 to "b12", 2 to "b13")
      },
      assertions = {
        assertStructure("""
          |r
          | a1
          |  b11
          |  b12
          |  b13
          """.trimMargin()
        )
      },
    )
  }

  @Test
  fun `sparse multiple insertions`() {
    testStructure(
      initOps = {
        setModelStructure("""
          |r
          | a1
          |  b11
          |   c111
          |  b12
          """.trimMargin()
        )
        sut.isRootVisible = true
        selectionModel.expect { repeat(3) { resetRowSelection() } }
      },
      modOps = {
        sut.setExpandedState("r/a1", true)
        sut.setExpandedState("r/a1/b11", true)
        node("r/a1").setLeaf(false) // to avoid auto-collapsing
        node("r/a1").insert(0 to "b10", 2 to "b115", 4 to "b13")
      },
      assertions = {
        assertStructure("""
          |r
          | a1
          |  b10
          |  b11
          |   c111
          |  b115
          |  b12
          |  b13
          """.trimMargin()
        )
      },
    )
  }

  @Test
  fun `change root`() {
    testStructure(
      initOps = {
        setModelStructure("""
          |r1
          | a1
          |  b11
          |   c111
          |  b12
          """.trimMargin()
        )
        sut.isRootVisible = true
        selectionModel.expect { clearSelection() }
      },
      modOps = {
        setModelStructure("""
          |r2
          | a2
        """.trimMargin())
      },
      assertions = {
        assertStructure("""
          |r2
          | a2
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
    selectionModel.expect {
      rowMapper = sut
    }
    initOps()
    replay(selectionModel) // start verification
    sut.selectionModel = selectionModel
    sut.model = model
    // This is usually handled by the tree UI, but we're unit testing here:
    model.addTreeModelListener(object : TreeModelListener {
      override fun treeNodesChanged(e: TreeModelEvent?) {
        sut.treeNodesChanged(e)
      }

      override fun treeNodesInserted(e: TreeModelEvent?) {
        sut.treeNodesInserted(e)
      }

      override fun treeNodesRemoved(e: TreeModelEvent?) {
        sut.treeNodesRemoved(e)
      }

      override fun treeStructureChanged(e: TreeModelEvent?) {
        sut.treeStructureChanged(e)
      }
    })
    modOps()
    assertions()
    verify(selectionModel) // complete verification
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

  /**
   * Just executes the supplied code block.
   *
   * Exists purely for readability reasons, as those method calls on the mock
   * can be rather confusing to the reader, especially to a one unfamiliar with EasyMock.
   *
   * @param function the code block to execute
   */
  private inline fun TreeSelectionModel.expect(function: TreeSelectionModel.() -> Unit) {
    function()
  }

  private fun AbstractLayoutCache.setExpandedState(path: String, isExpanded: Boolean) {
    setExpandedState(path(path), isExpanded)
  }

  private fun node(path: String) = path(path).lastPathComponent as Node

  private fun path(s: String): TreePath {
    val names = s.split('/')
    var path: TreePath? = null
    for (name in names) {
      if (path == null) {
        assertThat((model.root as Node).userObject).isEqualTo(name)
        path = TreePath(model.root)
      }
      else {
        val parent = path.lastPathComponent
        val child = (0 until model.getChildCount(parent))
          .map { model.getChild(parent, it) }
          .firstOrNull { (it as Node).userObject == name }
        requireNotNull(child) { "Node $name not found in path $s" }
        path = path.pathByAddingChild(child)
      }
    }
    return path!!
  }

  private fun Node.insert(name: String, index: Int) {
    model.insertNodeInto(Node(name), this, index)
  }

  private fun Node.insert(vararg nodes: Pair<Int, String>) {
    for ((i, name) in nodes) {
      insert(Node(name), i)
    }
    model.nodesWereInserted(this, nodes.map { it.first }.toIntArray())
  }

  private fun Node.remove() {
    model.removeNodeFromParent(this)
  }

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

    private var isLeaf: Boolean? = null

    override fun isLeaf(): Boolean = isLeaf ?: super.isLeaf()

    fun setLeaf(isLeaf: Boolean?) {
      this.isLeaf = isLeaf
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Node

      return userObject == other.userObject
    }

    override fun hashCode() = userObject.hashCode()

  }

}
