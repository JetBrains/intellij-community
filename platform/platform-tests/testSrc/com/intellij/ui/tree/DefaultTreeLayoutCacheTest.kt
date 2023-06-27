// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.ui.tree.ui.DefaultTreeLayoutCache
import org.assertj.core.api.AbstractBooleanAssert
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
  private lateinit var sut: AbstractLayoutCache
  private lateinit var heights: MutableMap<String, Int>

  @BeforeEach
  fun setUp() {
    model = DefaultTreeModel(null)
    sut = DefaultTreeLayoutCache(defaultRowHeight) { }
    heights = hashMapOf()
    sut.nodeDimensions = NodeDimensionsImpl(heights)
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
        selectionModel.expect { resetRowSelection() }
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
        selectionModel.expect { resetRowSelection() }
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
        selectionModel.expect { repeat(2) { resetRowSelection() } }
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
          resetRowSelection()
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
        selectionModel.expect { repeat(2) { resetRowSelection() } }
      },
      modOps = {
        sut.setExpandedState("r", false)
      },
      assertions = {
        assertStructure("r".trimMargin())
        assertThatExpanded("r").isFalse()
      },
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
      assertions = {
        assertStructure("".trimMargin())
        assertThatExpanded("r").isFalse()
      },
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
        selectionModel.expect { resetRowSelection() }
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
        assertThatExpanded("r").isTrue()
        assertThatExpanded("r/a1").isFalse()
        assertThatExpanded("r/a2").isFalse()
     },
    )
  }

  @Test
  fun `re-expand invisible root`() {
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
        sut.isRootVisible = false
        selectionModel.expect { repeat(2) { resetRowSelection() } }
      },
      modOps = {
        sut.setExpandedState("r", false)
        sut.setExpandedState("r", true)
      },
      assertions = {
        assertStructure("""
          | a1
          | a2
          """.trimMargin()
        )
        // The only difference between our implementations of these two is that for the invisible root,
        // getExpandedState() always returns false, but isExpanded() returns the actual state.
        // This is different from VariableHeightLayoutCache, but makes more sense, according to
        // the way it's actually used.
        assertThatExpandedState("r").isFalse()
        assertThatIsExpanded("r").isTrue()
        assertThatExpanded("r/a1").isFalse()
        assertThatExpanded("r/a2").isFalse()
     },
    )
  }

  @Test
  fun `re-expand visible root`() {
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
        selectionModel.expect { repeat(3) { resetRowSelection() } }
      },
      modOps = {
        sut.setExpandedState("r", false)
        sut.setExpandedState("r", true)
      },
      assertions = {
        assertStructure("""
          |r
          | a1
          | a2
          """.trimMargin()
        )
        assertThatExpandedState("r").isTrue()
        assertThatIsExpanded("r").isTrue()
        assertThatExpanded("r/a1").isFalse()
        assertThatExpanded("r/a2").isFalse()
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
        selectionModel.expect { repeat(3) { resetRowSelection() } }
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
        assertThatExpanded("r").isTrue()
        assertThatExpanded("r/a1").isTrue()
        assertThatExpanded("r/a2").isTrue()
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
          repeat(5) { resetRowSelection() }
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
        assertThatExpanded("r").isTrue()
        assertThatExpanded("r/a1").isTrue()
        assertThatExpanded("r/a2").isTrue()
      },
    )
  }

  @Test
  fun `collapse visible root with previously expanded children`() {
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
          repeat(4) { resetRowSelection() }
        }
      },
      modOps = {
        sut.setExpandedState("r", true)
        sut.setExpandedState("r/a1", true)
        sut.setExpandedState("r/a2", true)
        sut.setExpandedState("r", false)
      },
      assertions = {
        assertStructure("r")
        assertThatExpanded("r").isFalse()
        assertThatExpanded("r/a1").isFalse()
        assertThatExpanded("r/a2").isFalse()
      },
    )
  }

  @Test
  fun `expand a node deep down a not yet loaded branch`() {
    testStructure(
      initOps = {
        setModelStructure("""
          |r
          | a1
          |  b1
          |   c1
          |    d1
          """.trimMargin()
        )
        sut.isRootVisible = true
        selectionModel.expect { repeat(2) { resetRowSelection() } }
      },
      modOps = {
        sut.setExpandedState("r/a1/b1/c1", true)
      },
      assertions = {
        assertStructure("""
          |r
          | a1
          |  b1
          |   c1
          |    d1
          """.trimMargin()
        )
        assertThatExpanded("r").isTrue()
        assertThatExpanded("r/a1").isTrue()
        assertThatExpanded("r/a1/b1").isTrue()
        assertThatExpanded("r/a1/b1/c1").isTrue()
      },
    )
  }

  @Test
  fun `expand a non-existing child of a node with loaded children`() {
    testStructure(
      initOps = {
        setModelStructure("""
          |r
          | a1
          |  b1
          |   c1
          |    d1
          """.trimMargin()
        )
        sut.isRootVisible = true
        selectionModel.expect { resetRowSelection() }
      },
      modOps = {
        sut.setExpandedState(fakePath("r/a2"), true)
      },
      assertions = {
        assertStructure("""
          |r
          | a1
          """.trimMargin()
        )
        assertThatExpanded("r").isTrue()
        assertThatExpanded("r/a1").isFalse()
        assertThatExpanded("r/a1/b1").isFalse()
        assertThatExpanded("r/a1/b1/c1").isFalse()
      },
    )
  }

  @Test
  fun `expand a non-existing node deep down a not yet loaded branch`() {
    testStructure(
      initOps = {
        setModelStructure("""
          |r
          | a1
          |  b1
          |   c1
          |    d1
          """.trimMargin()
        )
        sut.isRootVisible = true
        selectionModel.expect { resetRowSelection() }
      },
      modOps = {
        sut.setExpandedState(fakePath("r/a1/b1/c1111"), true)
      },
      assertions = {
        assertStructure("""
          |r
          | a1
          """.trimMargin()
        )
        assertThatExpanded("r").isTrue()
        assertThatExpanded("r/a1").isFalse()
        assertThatExpanded("r/a1/b1").isFalse()
        assertThatExpanded("r/a1/b1/c1").isFalse()
      },
    )
  }

  @Test
  fun `expand a node from an alien root`() {
    testStructure(
      initOps = {
        setModelStructure("""
          |r
          | a1
          |  b1
          |   c1
          |    d1
          """.trimMargin()
        )
        sut.isRootVisible = true
        selectionModel.expect { resetRowSelection() }
      },
      modOps = {
        sut.setExpandedState(fakePath("r2/a1/b1"), true)
      },
      assertions = {
        assertStructure("""
          |r
          | a1
          """.trimMargin()
        )
        assertThatExpanded("r").isTrue()
        assertThatExpanded("r/a1").isFalse()
        assertThatExpanded("r/a1/b1").isFalse()
        assertThatExpanded("r/a1/b1/c1").isFalse()
      },
    )
  }

  @Test
  fun `expand an alien root`() {
    testStructure(
      initOps = {
        setModelStructure("""
          |r
          | a1
          |  b1
          |   c1
          |    d1
          """.trimMargin()
        )
        sut.isRootVisible = true
        selectionModel.expect { resetRowSelection() }
      },
      modOps = {
        sut.setExpandedState(fakePath("r2"), true)
      },
      assertions = {
        assertStructure("""
          |r
          | a1
          """.trimMargin()
        )
        assertThatExpanded("r").isTrue()
        assertThatExpanded("r/a1").isFalse()
        assertThatExpanded("r/a1/b1").isFalse()
        assertThatExpanded("r/a1/b1/c1").isFalse()
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
        selectionModel.expect { repeat(2) { resetRowSelection() } }
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
        assertThatExpanded("r").isTrue()
        assertThatExpanded("r/a1").isTrue()
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
        selectionModel.expect { repeat(4) { resetRowSelection() } }
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
        assertThatExpanded("r").isTrue()
        assertThatExpanded("r/a1").isTrue()
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
        selectionModel.expect { repeat(4) { resetRowSelection() } }
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
        assertThatExpanded("r").isTrue()
        assertThatExpanded("r/a1").isTrue()
        assertThatExpanded("r/a1/b10").isFalse()
        assertThatExpanded("r/a1/b11").isTrue()
        assertThatExpanded("r/a1/b115").isFalse()
        assertThatExpanded("r/a1/b12").isFalse()
        assertThatExpanded("r/a1/b13").isFalse()
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
        selectionModel.expect {
          repeat(2) { resetRowSelection() }
          clearSelection()
        }
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
        assertThatExpanded("r2").isTrue()
      },
    )
  }

  @Test
  fun `change invisible root`() {
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
        sut.isRootVisible = false
        selectionModel.expect {
          repeat(2) { resetRowSelection() }
          clearSelection()
        }
      },
      modOps = {
        setModelStructure("""
          |r2
          | a2
        """.trimMargin())
      },
      assertions = {
        assertStructure("""
          | a2
          """.trimMargin()
        )
        assertThatExpandedState("r2").isFalse()
        assertThatIsExpanded("r2").isTrue()
      },
    )
  }

  @Test
  fun `row height - empty tree`() {
    testSizes(
      initOps = {
        sut.isRootVisible = false
      },
      modOps = { },
      assertions = {
        assertSize(0, 0)
      },
    )
  }

  @Test
  fun `row height - variable height, all default`() {
    testSizes(
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
      },
      modOps = {
        sut.setExpandedState("r1/a1/b11", true)
      },
      assertions = {
        assertSize(3 * indent + "c111".length, defaultRowHeight * 5)
      },
    )
  }

  @Test
  fun `row height - fixed height`() {
    testSizes(
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
        sut.rowHeight = 10
      },
      modOps = {
        sut.setExpandedState("r1/a1/b11", true)
      },
      assertions = {
        assertSize(3 * indent + "c111".length, 10 * 5)
      },
    )
  }

  @Test
  fun `row height - variable height, some different`() {
    testSizes(
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
        heights["b11"] = 10
        heights["b12"] = 15
      },
      modOps = {
        sut.setExpandedState("r1/a1/b11", true)
      },
      assertions = {
        assertHeight(defaultRowHeight * 5) // default height
        // The first line:
        assertWidth(0, defaultRowHeight - 1, "r1".length)
        assertHeight(defaultRowHeight * 5) // still default, as b11 is not visible
        // Now query three lines:
        assertWidth(0, defaultRowHeight * 2 + 1, indent * 2 + "b11".length)
        // Updated height since the b11 row became "visible" after the last query:
        assertHeight(defaultRowHeight * 5 - (defaultRowHeight - 10))
        // Now query the last line:
        assertWidth(defaultRowHeight * 4 - (defaultRowHeight - 10), defaultRowHeight * 5 - (defaultRowHeight - 10), indent * 2 + "b12".length)
        // Updated height of all rows now:
        assertHeight(defaultRowHeight * 5 - (defaultRowHeight - 10) - (defaultRowHeight - 15))
      },
    )
  }

  private fun testStructure(
    initOps: () -> Unit = { },
    modOps: () -> Unit = { },
    assertions: () -> Unit = { },
  ) {
    selectionModel = strictMock(TreeSelectionModel::class.java)
    selectionModel.expect {
      rowMapper = sut // called from setSelectionModel()
    }
    initOps()
    replay(selectionModel) // start verification
    setModels()
    modOps()
    assertions()
    verify(selectionModel) // complete verification
  }

  private fun testSizes(
    initOps: () -> Unit = { },
    modOps: () -> Unit = { },
    assertions: () -> Unit = { },
  ) {
    selectionModel = niceMock(TreeSelectionModel::class.java) // Verification not needed in these tests.
    initOps()
    setModels()
    modOps()
    assertions()
  }

  private fun setModels() {
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

  private fun assertThatExpanded(path: String): AbstractBooleanAssert<*> {
    val expandedState = sut.getExpandedState(path)
    val isExpanded = sut.isExpanded(path)
    val both = when {
      expandedState && isExpanded -> true
      !expandedState && !isExpanded -> false
      else -> null
    }
    return assertThat(both).`as`("getExpandedState($path), isExpanded($path)")
  }

  private fun assertThatExpandedState(path: String): AbstractBooleanAssert<*> =
    assertThat(sut.getExpandedState(path)).`as`("getExpandedState($path)")

  private fun assertThatIsExpanded(path: String): AbstractBooleanAssert<*> =
    assertThat(sut.isExpanded(path)).`as`("isExpanded($path)")

  private fun assertSize(width: Int, height: Int) {
    val actualWidth = sut.getPreferredWidth(null)
    val actualHeight = sut.preferredHeight
    assertThat(actualWidth).`as`("width").isEqualTo(width)
    assertThat(actualHeight).`as`("height").isEqualTo(height)
  }

  private fun assertHeight(height: Int) {
    val actualHeight = sut.preferredHeight
    assertThat(actualHeight).`as`("height").isEqualTo(height)
  }

  private fun assertWidth(from: Int, to: Int, width: Int) {
    val actualWidth = sut.getPreferredWidth(Rectangle(0, from, 0, to - from))
    assertThat(actualWidth).`as`("width for [%d,%d]", from, to).isEqualTo(width)
  }

  private fun AbstractLayoutCache.setExpandedState(path: String, isExpanded: Boolean) {
    setExpandedState(path(path), isExpanded)
  }

  private fun AbstractLayoutCache.getExpandedState(path: String) = getExpandedState(path(path))

  private fun AbstractLayoutCache.isExpanded(path: String) = isExpanded(path(path))

  private fun node(path: String) = path(path).lastPathComponent as Node

  private fun path(s: String): TreePath = pathImpl(s, true)

  private fun fakePath(s: String): TreePath = pathImpl(s, false)

  private fun pathImpl(s: String, onlyExisting: Boolean): TreePath {
    val names = s.split('/')
    var path: TreePath? = null
    for (name in names) {
      if (path == null) {
        if (onlyExisting) {
          assertThat((model.root as Node).userObject).isEqualTo(name)
          path = TreePath(model.root)
        }
        else {
          path = TreePath(Node(name))
        }
      }
      else {
        val parent = path.lastPathComponent
        var child = (0 until model.getChildCount(parent))
          .map { model.getChild(parent, it) }
          .firstOrNull { (it as Node).userObject == name }
        if (onlyExisting) {
          requireNotNull(child) { "Node $name not found in path $s" }
        }
        else {
          child = Node(name)
        }
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
    val visiblePaths = mutableListOf<TreePath>()
    for (row in 0 until sut.rowCount) {
      val path = sut.getPathForRow(row)
      visiblePaths += path
      assertThat(sut.getRowForPath(path)).`as`("row %d", row).isEqualTo(row)
    }
    for ((i, path) in visiblePaths.withIndex()) {
      assertThat(sut.getVisiblePathsFrom(path).toList()).isEqualTo(visiblePaths.subList(i, visiblePaths.size))
    }
    assertPathVisibilityInvariants(model.root?.let { CachingTreePath(it) }, visiblePaths.toSet())
  }

  private fun assertPathVisibilityInvariants(path: TreePath?, visiblePaths: Set<TreePath>) {
    if (path == null) {
      return
    }
    assertThat(sut.getVisibleChildCount(path)).`as`("getVisibleChildCount(%s)", path).isEqualTo(countVisibleChildren(path, visiblePaths))
    if (path !in visiblePaths) {
      assertThat(sut.getRowForPath(path)).isEqualTo(-1)
    }
    val value = path.lastPathComponent
    for (i in 0 until model.getChildCount(value)) {
      val child = model.getChild(value, i)
      assertPathVisibilityInvariants(path.pathByAddingChild(child), visiblePaths)
    }
  }

  private fun countVisibleChildren(path: TreePath, visiblePaths: Set<TreePath>): Int {
    var result = 0
    val value = path.lastPathComponent
    for (i in 0 until model.getChildCount(value)) {
      val child = model.getChild(value, i)
      val childPath = path.pathByAddingChild(child)
      if (childPath in visiblePaths) {
        ++result // the child itself
        result += countVisibleChildren(childPath, visiblePaths)
      }
    }
    return result
  }

  private class NodeDimensionsImpl(private val heights: Map<String, Int>) : NodeDimensions() {
    override fun getNodeDimensions(value: Any?, row: Int, depth: Int, expanded: Boolean, bounds: Rectangle?): Rectangle =
      (bounds ?: Rectangle()).apply {
        x = depth * indent
        value as Node
        val userObject = value.userObject as String
        width = userObject.length
        height = heights[userObject] ?: defaultRowHeight
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
