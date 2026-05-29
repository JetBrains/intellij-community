// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetUserMove
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSorterTest {

  private data class TestOrderable(
    override val orderId: String,
    override val order: LoadingOrder
  ) : LoadingOrder.Orderable {
    override fun toString(): String = "Widget(id=$orderId, order=$order)"
  }

  @Test
  fun `should sort widgets by loading order`() {
    val widgets = mutableListOf(
      TestOrderable("LineSeparator", LoadingOrder.after("Position")),
      TestOrderable("Position", LoadingOrder.ANY),
      TestOrderable("Encoding", LoadingOrder.after("LineSeparator"))
    )

    val sorter = IdeStatusBarImpl.WidgetSorter(mutableListOf()) { }
    sorter.sortWidgets(widgets.cast())

    assertEquals("Position", widgets[0].orderId)
    assertEquals("LineSeparator", widgets[1].orderId)
    assertEquals("Encoding", widgets[2].orderId)
  }

  @Test
  fun `should apply user moves after loading order sort`() {
    val widgets = mutableListOf(
      TestOrderable("Position", LoadingOrder.ANY),
      TestOrderable("LineSeparator", LoadingOrder.after("Position")),
      TestOrderable("Encoding", LoadingOrder.after("LineSeparator"))
    )

    // Move Encoding before Position
    val moves = mutableListOf(StatusBarWidgetUserMove("Encoding", "Position"))
    val sorter = IdeStatusBarImpl.WidgetSorter(moves) { }
    sorter.sortWidgets(widgets.cast())

    assertEquals("Encoding", widgets[0].orderId)
    assertEquals("Position", widgets[1].orderId)
    assertEquals("LineSeparator", widgets[2].orderId)
  }

  @Test
  fun `should handle multiple user moves`() {
    val widgets = mutableListOf(
      TestOrderable("A", LoadingOrder.ANY),
      TestOrderable("B", LoadingOrder.ANY),
      TestOrderable("C", LoadingOrder.ANY)
    )

    // Initial order A, B, C
    // Move C to A -> C, A, B
    // Move B to C -> B, C, A
    val moves = mutableListOf(
        StatusBarWidgetUserMove("C", "A"),
        StatusBarWidgetUserMove("B", "C")
    )
    val sorter = IdeStatusBarImpl.WidgetSorter(moves) { }
    sorter.sortWidgets(widgets.cast())

    assertEquals("B", widgets[0].orderId)
    assertEquals("C", widgets[1].orderId)
    assertEquals("A", widgets[2].orderId)
  }

  @Test
  fun `should handle complex dependencies from example`() {
    // This is actual Widget configuration taken from running instance, below as `widgets`
    // [Widget(id=Position, order=ANY, position=RIGHT),
    //  Widget(id=LanguageServiceStatusBarWidget, order=after Position, after AIAssistant, before LineSeparator, position=RIGHT),
    //  Widget(id=LineSeparator, order=after Position, position=RIGHT),
    //  Widget(id=Encoding, order=after LineSeparator, position=RIGHT),
    //  Widget(id=PowerSaveMode, order=after Encoding, position=RIGHT),
    //  Widget(id=InsertOverwrite, order=after PowerSaveMode, position=RIGHT),
    //  Widget(id=CodeStyleStatusBarWidget, order=after InsertOverwrite, position=RIGHT),
    //  Widget(id=JSONSchemaSelector, order=after CodeStyleStatusBarWidget, before ReadOnlyAttribute, position=RIGHT),
    //  Widget(id=largeFileEncodingWidget, order=after PowerSaveMode, position=RIGHT),
    //  Widget(id=ReadOnlyAttribute, order=after InsertOverwrite, position=RIGHT),
    //  Widget(id=FatalError, order=after Notifications, position=RIGHT)]

    val widgets = mutableListOf(
      TestOrderable("Position", LoadingOrder.ANY),
      TestOrderable("LanguageServiceStatusBarWidget", LoadingOrder.readOrder("after Position, before LineSeparator")),
      TestOrderable("LineSeparator", LoadingOrder.after("Position")),
      TestOrderable("Encoding", LoadingOrder.after("LineSeparator")),
      TestOrderable("PowerSaveMode", LoadingOrder.after("Encoding")),
      TestOrderable("InsertOverwrite", LoadingOrder.after("PowerSaveMode")),
      TestOrderable("CodeStyleStatusBarWidget", LoadingOrder.after("InsertOverwrite")),
      TestOrderable("JSONSchemaSelector", LoadingOrder.readOrder("after CodeStyleStatusBarWidget, before ReadOnlyAttribute")),
      TestOrderable("largeFileEncodingWidget", LoadingOrder.after("PowerSaveMode")),
      TestOrderable("ReadOnlyAttribute", LoadingOrder.after("InsertOverwrite")),
      TestOrderable("FatalError", LoadingOrder.after("Notifications")), // Notifications is missing
      TestOrderable("Notifications", LoadingOrder.ANY) // Added to satisfy FatalError dependency if we want to test it
    )

    val sorter = IdeStatusBarImpl.WidgetSorter(mutableListOf()) { }
    sorter.sortWidgets(widgets.cast())

    // Verify some key orderings
    assertBefore(widgets, "Position", "LanguageServiceStatusBarWidget")
    assertBefore(widgets, "LanguageServiceStatusBarWidget", "LineSeparator")
    assertBefore(widgets, "LineSeparator", "Encoding")
    assertBefore(widgets, "Encoding", "PowerSaveMode")
    assertBefore(widgets, "PowerSaveMode", "InsertOverwrite")
    assertBefore(widgets, "InsertOverwrite", "CodeStyleStatusBarWidget")
    assertBefore(widgets, "CodeStyleStatusBarWidget", "JSONSchemaSelector")
    assertBefore(widgets, "JSONSchemaSelector", "ReadOnlyAttribute")
    assertBefore(widgets, "PowerSaveMode", "largeFileEncodingWidget")
    assertBefore(widgets, "Notifications", "FatalError")
  }

  @Test
  fun `should persist moves when reordering`() {
    var persistedMoves: List<StatusBarWidgetUserMove>? = null
    val sorter = IdeStatusBarImpl.WidgetSorter(mutableListOf()) { persistedMoves = it }

    sorter.reorder("A", "B")

    assertEquals(1, persistedMoves?.size)
    assertEquals("A", persistedMoves?.get(0)?.source)
    assertEquals("B", persistedMoves?.get(0)?.target)
  }

  @Test
  fun `should replace existing move for the same source widget`() {
    var persistedMoves: List<StatusBarWidgetUserMove>? = null
    val initialMoves = mutableListOf(StatusBarWidgetUserMove("A", "B"))
    val sorter = IdeStatusBarImpl.WidgetSorter(initialMoves) { persistedMoves = it }

    sorter.reorder("A", "C")

    assertEquals(1, persistedMoves?.size)
    assertEquals("A", persistedMoves?.get(0)?.source)
    assertEquals("C", persistedMoves?.get(0)?.target)
  }

  private fun assertBefore(widgets: List<LoadingOrder.Orderable>, beforeId: String, afterId: String) {
    val beforeIndex = widgets.indexOfFirst { it.orderId == beforeId }
    val afterIndex = widgets.indexOfFirst { it.orderId == afterId }

    if (beforeIndex == -1) throw AssertionError("Widget $beforeId not found")
    if (afterIndex == -1) throw AssertionError("Widget $afterId not found")

    if (beforeIndex >= afterIndex) {
      throw AssertionError("Expected $beforeId to be before $afterId, but was at $beforeIndex and $afterIndex respectively. List: ${widgets.map { it.orderId }}")
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> MutableList<T>.cast(): MutableList<LoadingOrder.Orderable> = this as MutableList<LoadingOrder.Orderable>
}
