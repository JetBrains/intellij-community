// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ide.ui.laf.darcula.ui.DarculaCheckBoxBorder
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tree.ui.DefaultTreeUI
import com.intellij.util.ui.ColorIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.UIManager
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

@TestApplication
internal class ChangesTreeCellRendererTest {
  @Test
  fun `structural icons keep their position as the tree selection changes`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val fixture = withContext(Dispatchers.EDT) {
      createPaintFixture(project)
    }

    val baselines = mutableMapOf<Boolean, Map<String, Rectangle>>()
    for (showCheckboxes in listOf(true, false, true)) {
      withContext(Dispatchers.EDT) {
        fixture.tree.setShowCheckboxes(showCheckboxes)
      }
      val baseline = markerBounds(withContext(Dispatchers.EDT) { fixture.paint(null) })
      val previousBaseline = baselines.putIfAbsent(showCheckboxes, baseline)
      if (previousBaseline != null) {
        assertEquals(previousBaseline, baseline, "Restore icon positions after toggling the checkboxes")
      }
      for (selectedNode in listOf(1, 2, 0, 3, null)) {
        val actual = markerBounds(withContext(Dispatchers.EDT) { fixture.paint(selectedNode) })
        assertEquals(baseline, actual, "Icon positions with checkboxes=$showCheckboxes and selected node=$selectedNode")
      }
    }

    val withCheckboxes = baselines.getValue(true)
    val withoutCheckboxes = baselines.getValue(false)
    val offsets = withCheckboxes.map { (name, bounds) -> bounds.x - withoutCheckboxes.getValue(name).x }
    assertTrue(offsets.first() > 0, "Reserve space for the file checkbox")
    assertEquals(1, offsets.distinct().size, "Reserve the same checkbox width for files and structural elements")
  }

  @Test
  fun `structural icons keep their position when only structural rows repaint`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val previousBorder = withContext(Dispatchers.EDT) {
      UIManager.put("CheckBox.border", DarculaCheckBoxBorder())
    }
    try {
      val fixture = withContext(Dispatchers.EDT) {
        createPaintFixture(project)
      }
      for (showCheckboxes in listOf(true, false)) {
        withContext(Dispatchers.EDT) {
          fixture.tree.setShowCheckboxes(showCheckboxes)
        }
        val baseline = markerBounds(withContext(Dispatchers.EDT) { fixture.paint(null) })
        for (focused in listOf(true, false, true)) {
          for (selectedNode in listOf(1, 2, 0, null)) {
            val partial = markerBounds(withContext(Dispatchers.EDT) {
              fixture.paint(selectedNode, focused, paintedNodes = 1..2)
            })
            val state = "checkboxes=$showCheckboxes, focused=$focused, selected node=$selectedNode"
            assertEquals(baseline.filterKeys { it in partial }, partial, "Icon positions after a partial paint with $state")
            val full = markerBounds(withContext(Dispatchers.EDT) { fixture.paint(selectedNode, focused) })
            assertEquals(baseline, full, "Icon positions after a full paint with $state")
          }
        }
      }
    }
    finally {
      withContext(NonCancellable + Dispatchers.EDT) {
        UIManager.put("CheckBox.border", previousBorder)
      }
    }
  }

  private fun createPaintFixture(project: Project): PaintFixture {
    val file = MarkerFileNode("Sample.java", Color(231, 17, 183))
    val first = MarkerStructuralNode("firstMethod()", Color(19, 211, 173))
    val second = MarkerStructuralNode("secondMethod()", Color(233, 129, 17))
    val last = MarkerFileNode("Other.java", Color(113, 37, 229))
    file.add(first)
    file.add(second)
    val root = ChangesBrowserNode.createRoot().apply {
      add(file)
      add(last)
    }
    val tree = MarkerTree(project).apply {
      setUI(DefaultTreeUI())
      model = DefaultTreeModel(root)
      expandPath(TreePath(file.path))
      setSize(640, 300)
      doLayout()
    }
    return PaintFixture(tree, listOf(file, first, second, last))
  }

  private fun markerBounds(paint: TreePaint): Map<String, Rectangle> = paint.rows.associate { row ->
    val pixels = buildList {
      for (y in row.bounds.y until row.bounds.y + row.bounds.height) {
        for (x in 0 until paint.image.width) {
          if (paint.image.getRGB(x, y) == row.color.rgb) add(x to y)
        }
      }
    }
    assertTrue(pixels.isNotEmpty(), "Paint the icon for ${row.name}")
    val left = pixels.minOf { it.first }
    val top = pixels.minOf { it.second }
    row.name to Rectangle(left, top, pixels.maxOf { it.first } - left + 1, pixels.maxOf { it.second } - top + 1)
  }

  private class MarkerTree(project: Project) : ChangesTree(project, true, false) {
    var focused: Boolean = true

    override fun rebuildTree() = Unit
    override fun hasFocus(): Boolean = focused
  }

  private class PaintFixture(val tree: MarkerTree, private val nodes: List<MarkerNode>) {
    fun paint(selectedNode: Int?, focused: Boolean = true, paintedNodes: IntRange = nodes.indices): TreePaint {
      tree.focused = focused
      if (selectedNode == null) tree.clearSelection()
      else tree.selectionPath = TreePath(nodes[selectedNode].node.path)
      val rows = paintedNodes.map { index ->
        val marker = nodes[index]
        PaintedRow(marker.node.textPresentation, marker.color, checkNotNull(tree.getPathBounds(TreePath(marker.node.path))))
      }
      val image = BufferedImage(tree.width, tree.height, BufferedImage.TYPE_INT_ARGB)
      val graphics = image.createGraphics()
      try {
        val firstRow = rows.first().bounds
        val lastRow = rows.last().bounds
        graphics.clipRect(0, firstRow.y, tree.width, lastRow.y + lastRow.height - firstRow.y)
        tree.paint(graphics)
      }
      finally {
        graphics.dispose()
      }
      return TreePaint(image, rows)
    }
  }

  private interface MarkerNode {
    val node: ChangesBrowserNode<*>
    val color: Color
  }

  private class MarkerFileNode(private val name: String, override val color: Color) : ChangesBrowserNode<String>(name), MarkerNode {
    override val node: ChangesBrowserNode<*> get() = this
    private val markerIcon = ColorIcon(12, color)

    override fun getTextPresentation(): String = name

    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
      renderer.append(textPresentation, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      renderer.icon = markerIcon
    }
  }

  private class MarkerStructuralNode(private val name: String, override val color: Color) : ChangesBrowserStructuralNode<String>(name), MarkerNode {
    override val node: ChangesBrowserNode<*> get() = this
    private val markerIcon = ColorIcon(12, color)

    override fun getNavigatable(): Navigatable? = null
    override fun getTextPresentation(): String = name

    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
      renderer.append(textPresentation, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      renderer.icon = markerIcon
    }
  }

  private data class PaintedRow(val name: String, val color: Color, val bounds: Rectangle)
  private data class TreePaint(val image: BufferedImage, val rows: List<PaintedRow>)

  companion object {
    private val projectFixture = projectFixture()
  }
}
