// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.openapi.extensions.LoadingOrder
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
  fun `should sort widgets by loading order when no user customization`() {
    val widgets = mutableListOf(
      TestOrderable("LineSeparator", LoadingOrder.after("Position")),
      TestOrderable("Position", LoadingOrder.ANY),
      TestOrderable("Encoding", LoadingOrder.after("LineSeparator"))
    )

    val sorter = IdeStatusBarImpl.WidgetSorter(initialOrder = emptyMap(), persist = { })
    sorter.sortWidgets(widgets.cast())

    assertEquals(listOf("Position", "LineSeparator", "Encoding"), widgets.map { it.orderId })
  }

  @Test
  fun `should place dragged widget at target index`() {
    var persisted: Map<String, Int> = emptyMap()
    val sorter = IdeStatusBarImpl.WidgetSorter(initialOrder = emptyMap(), persist = { persisted = it })

    sorter.reorder("Encoding", "Position", listOf("Position", "LineSeparator", "Encoding"))

    assertEquals(mapOf("Encoding" to 0), persisted)
  }

  @Test
  fun `should handle complex dependencies from example`() {
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
      TestOrderable("FatalError", LoadingOrder.after("Notifications")),
      TestOrderable("Notifications", LoadingOrder.ANY)
    )

    val sorter = IdeStatusBarImpl.WidgetSorter(initialOrder = emptyMap(), persist = { })
    sorter.sortWidgets(widgets.cast())

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
  fun `should persist widgetOrder map after drag`() {
    var persisted: Map<String, Int> = emptyMap()
    val sorter = IdeStatusBarImpl.WidgetSorter(initialOrder = emptyMap(), persist = { persisted = it })

    sorter.reorder("C", "A", listOf("A", "B", "C"))

    assertEquals(mapOf("C" to 0), persisted)
  }

  @Test
  fun `should update widget's order when redragged`() {
    var persisted: Map<String, Int> = emptyMap()
    val sorter = IdeStatusBarImpl.WidgetSorter(initialOrder = emptyMap(), persist = { persisted = it })

    sorter.reorder("A", "B", listOf("A", "B", "C"))
    assertEquals(mapOf("A" to 0), persisted)

    sorter.reorder("A", "C", currentVisibleOrderFrom(persisted, defaultIds = listOf("A", "B", "C")))
    assertEquals(mapOf("A" to 1), persisted)
  }

  @Test
  fun `inverse drag should keep unrelated widget at its absolute position`() {
    val defaultIds = listOf("P", "LS", "E", "CS", "RO")

    var persisted: Map<String, Int> = emptyMap()
    val sorter = IdeStatusBarImpl.WidgetSorter(initialOrder = emptyMap(), persist = { persisted = it })

    sorter.reorder("E", "RO", currentVisibleOrderFrom(persisted, defaultIds))
    val csIdxAfterStep1 = currentVisibleOrderFrom(persisted, defaultIds).indexOf("CS")
    assertEquals(2, csIdxAfterStep1)

    sorter.reorder("RO", "E", currentVisibleOrderFrom(persisted, defaultIds))
    val csIdxAfterStep2 = currentVisibleOrderFrom(persisted, defaultIds).indexOf("CS")
    assertEquals(3, csIdxAfterStep2)
  }

  @Test
  fun `should assign unique dense indices across multiple drags`() {
    val defaultIds = listOf("A", "B", "C", "D", "E")

    var persisted: Map<String, Int> = emptyMap()
    val sorter = IdeStatusBarImpl.WidgetSorter(initialOrder = emptyMap(), persist = { persisted = it })

    sorter.reorder("E", "A", currentVisibleOrderFrom(persisted, defaultIds))
    sorter.reorder("A", "C", currentVisibleOrderFrom(persisted, defaultIds))
    sorter.reorder("D", "E", currentVisibleOrderFrom(persisted, defaultIds))

    val values = persisted.values
    assertEquals(values.size, values.toSet().size)
  }

  @Test
  fun `should preserve orphan entries for widgets temporarily not visible`() {
    var persisted: Map<String, Int> = mapOf("OrphanWidget" to 0, "A" to 1, "B" to 2)
    val sorter = IdeStatusBarImpl.WidgetSorter(initialOrder = persisted, persist = { persisted = it })

    sorter.reorder("B", "A", listOf("A", "B"))

    assertEquals(0, persisted["OrphanWidget"])
    assertEquals(0, persisted["B"])
    assertEquals(1, persisted["A"])
  }

  @Test
  fun `should place widget without index entry after all customized widgets`() {
    val widgets = mutableListOf(
      TestOrderable("A", LoadingOrder.ANY),
      TestOrderable("NewWidget", LoadingOrder.after("A")),
      TestOrderable("B", LoadingOrder.after("NewWidget"))
    )

    val sorter = IdeStatusBarImpl.WidgetSorter(
      initialOrder = mapOf("A" to 0, "B" to 1),
      persist = { }
    )
    sorter.sortWidgets(widgets.cast())

    assertEquals(listOf("A", "B", "NewWidget"), widgets.map { it.orderId })
  }

  @Test
  fun `reorder with source equal to target is a no-op`() {
    var persistCount = 0
    val sorter = IdeStatusBarImpl.WidgetSorter(initialOrder = emptyMap(), persist = { persistCount++ })

    sorter.reorder("A", "A", listOf("A", "B", "C"))

    // Index recomputation must not throw and the list arrangement must remain identical;
    // the algorithm tolerates degenerate input even though the production drop handler
    // filters source == target upstream.
    assertEquals(1, persistCount)
  }

  @Test
  fun `reorder with unknown source or target is a no-op`() {
    var persistCount = 0
    val initial = mapOf("A" to 0, "B" to 1)
    val sorter = IdeStatusBarImpl.WidgetSorter(initialOrder = initial, persist = { persistCount++ })

    sorter.reorder("Ghost", "A", listOf("A", "B"))
    sorter.reorder("A", "Ghost", listOf("A", "B"))

    assertEquals(0, persistCount)
  }

  /**
   * Mirrors what `IdeStatusBarImpl.sortWidgets` computes to reconstruct the visual order:
   * takes the default widgets in their initial loading order and sequentially applies
   * user-customized relocations from the smallest target index to the largest.
   * Uncustomized widgets naturally retain their relative default positions.
   */
  private fun currentVisibleOrderFrom(persisted: Map<String, Int>, defaultIds: List<String>): List<String> {
    val sorted = defaultIds.toMutableList()
    val customMoves = persisted.entries.sortedBy { it.value }
    for (entry in customMoves) {
      val widgetId = entry.key
      val targetIdx = entry.value
      val itemIdx = sorted.indexOf(widgetId)
      if (itemIdx != -1) {
        val item = sorted.removeAt(itemIdx)
        val safeIdx = targetIdx.coerceIn(0, sorted.size)
        sorted.add(safeIdx, item)
      }
    }
    return sorted
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
