// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.table

import com.intellij.openapi.application.UI
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableColumn
import javax.swing.table.TableModel

@TestApplication
internal class ColumnHeaderControllerTest {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun panelsFollowColumnMovesAndWidths(rightToLeft: Boolean): Unit = timeoutRunBlocking {
    val (indices, bounds) = withContext(Dispatchers.UI) {
      val context = TestContext(rightToLeft)
      val controller = TestController(context)
      try {
        context.columnModel.moveColumn(0, 1)
        context.columnModel.getColumn(0).width = 90
        controller.columns.map { it.tableColumn.modelIndex } to controller.columns.map { Rectangle(it.bounds) }
      }
      finally {
        controller.detach()
      }
    }
    assertEquals(listOf(1, 0), indices)
    val expected = if (rightToLeft) listOf(Rectangle(40, 0, 90, 20), Rectangle(0, 0, 40, 20))
    else listOf(Rectangle(0, 0, 90, 20), Rectangle(90, 0, 40, 20))
    assertEquals(expected, bounds)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun panelsFollowTheDraggedColumn(rightToLeft: Boolean): Unit = timeoutRunBlocking {
    val (initialX, draggedX, paintsAboveNeighbour) = withContext(Dispatchers.UI) {
      val context = TestContext(rightToLeft)
      val controller = TestController(context)
      try {
        val panel = controller.columns[1]
        val neighbor = controller.columns[0]
        val initialX = panel.x
        context.draggedColumn = panel.tableColumn
        context.draggedDistance = if (rightToLeft) 15 else -15
        context.columnModel.moveColumn(1, 1)
        Triple(initialX, panel.x, controller.getComponentZOrder(panel) < controller.getComponentZOrder(neighbor))
      }
      finally {
        controller.detach()
      }
    }
    assertEquals(initialX + if (rightToLeft) 15 else -15, draggedX)
    assertTrue(paintsAboveNeighbour)
  }

  @Test
  fun addingAndRemovingColumnsUpdatesThePanels(): Unit = timeoutRunBlocking {
    val (controller, removed) = withContext(Dispatchers.UI) {
      val context = TestContext()
      val controller = TestController(context)
      val removed = controller.columns.first()
      context.columnModel.addColumn(TableColumn(2, 60))
      context.columnModel.removeColumn(removed.tableColumn)
      controller to removed
    }
    try {
      drainEvents()
      val (indices, bounds, count) = withContext(Dispatchers.UI) {
        Triple(controller.columns.map { it.tableColumn.modelIndex },
               controller.columns.map { Rectangle(it.bounds) },
               controller.componentCount)
      }
      assertEquals(listOf(1, 2), indices)
      assertEquals(listOf(Rectangle(0, 0, 80, 20), Rectangle(80, 0, 60, 20)), bounds)
      assertTrue(removed.detached)
      assertEquals(2, count)
      assertTrue(controller.updates > 0)
    }
    finally {
      withContext(Dispatchers.UI) { controller.detach() }
    }
  }

  @Test
  fun detachingCancelsQueuedUpdatesAndRemovesListeners(): Unit = timeoutRunBlocking {
    val (controller, panels, hasListeners) = withContext(Dispatchers.UI) {
      val context = TestContext()
      val controller = TestController(context)
      val panels = controller.columns.toList()
      context.columnModel.addColumn(TableColumn(2, 60))
      controller.detach()
      controller.detach()
      Triple(controller, panels, context.columnModel.columnModelListeners.contains(controller))
    }
    drainEvents()
    assertEquals(0, controller.updates)
    assertFalse(hasListeners)
    assertTrue(panels.all { it.detached })
    assertTrue(controller.columns.isEmpty())
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun replacingAModelCancelsUpdatesForTheOldModel(replaceColumnModel: Boolean): Unit = timeoutRunBlocking {
    val controller = withContext(Dispatchers.UI) {
      val context = TestContext()
      TestController(context).also {
        context.columnModel.addColumn(TableColumn(2, 60))
        if (replaceColumnModel) context.columnModel = DefaultTableColumnModel()
        else context.model = DefaultTableModel(0, 3)
      }
    }
    try {
      drainEvents()
      assertEquals(0, controller.updates)
    }
    finally {
      withContext(Dispatchers.UI) { controller.detach() }
    }
  }

  /** Subclasses of AdditionalTableHeader.ColumnsControllerPanel, such as filter headers, balance state in every queued update. */
  @Test
  fun aTablePanelRunsQueuedUpdatesAfterDetach(): Unit = timeoutRunBlocking {
    val panel = withContext(Dispatchers.UI) {
      val table = JTable(DefaultTableModel(0, 2))
      TablePanel(table).also {
        table.columnModel.addColumn(TableColumn(2, 60))
        it.detach()
      }
    }
    drainEvents()
    assertEquals(1, panel.updates)
  }

  private suspend fun drainEvents() {
    val drained = CompletableDeferred<Unit>()
    SwingUtilities.invokeLater { drained.complete(Unit) }
    drained.await()
  }

  private class TablePanel(table: JTable) : AdditionalTableHeader.ColumnsControllerPanel(table) {
    var updates = 0
      private set

    init {
      myPreferredSize = Dimension(0, 20)
      for (index in 0 until tableColumnModel.columnCount) createColumn(index)
      tableColumnModel.addColumnModelListener(this)
    }

    override fun createColumn(columnView: Int): AdditionalPanel = Panel(tableColumnModel.getColumn(columnView)).also {
      columns.add(it)
      add(it)
    }

    override fun computeMyPreferredSize(): Dimension = Dimension(0, 20)

    override fun updateColumns() {
      updates++
    }

    inner class Panel(column: TableColumn) : AdditionalPanel(column) {
      override fun detach() {}

      override fun updateHeight() {}
    }
  }

  private class TestContext(rightToLeft: Boolean = false) : ColumnHeaderController.Context {
    override var columnModel: DefaultTableColumnModel = DefaultTableColumnModel().apply {
      addColumn(TableColumn(0, 40))
      addColumn(TableColumn(1, 80))
    }
    override var model: TableModel = DefaultTableModel(0, 3)
    override val componentOrientation: ComponentOrientation =
      if (rightToLeft) ComponentOrientation.RIGHT_TO_LEFT else ComponentOrientation.LEFT_TO_RIGHT
    override var draggedColumn: TableColumn? = null
    override var draggedDistance: Int = 0
  }

  private class TestController(context: Context) : ColumnHeaderController<TestController.Panel>(context) {
    var updates = 0
      private set

    init {
      myPreferredSize = Dimension(0, 20)
      for (index in 0 until tableColumnModel.columnCount) createColumn(index)
      tableColumnModel.addColumnModelListener(this)
      placeComponents()
    }

    override fun createColumn(columnView: Int): Panel = Panel(tableColumnModel.getColumn(columnView)).also {
      columns.add(it)
      add(it)
    }

    override fun computeMyPreferredSize(): Dimension = Dimension(0, 20)

    override fun updateColumns() {
      updates++
      placeComponents()
    }

    private fun layoutColumns() {
      placeComponents()
    }

    inner class Panel(column: TableColumn) : ColumnPanel(column, ::layoutColumns) {
      var detached = false
        private set

      init {
        myWidth = column.width
        column.addPropertyChangeListener(this)
      }

      override fun detach() {
        detached = true
        tableColumn.removePropertyChangeListener(this)
      }

      override fun updateHeight() {}
    }
  }
}
