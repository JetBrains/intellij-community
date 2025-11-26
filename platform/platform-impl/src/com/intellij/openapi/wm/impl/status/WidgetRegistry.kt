// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.wm.impl.status

import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.LoadingOrder.Orderable
import com.intellij.openapi.wm.StatusBarWidget
import javax.swing.JComponent

internal enum class Position {
  LEFT,
  RIGHT,
  CENTER
}

internal class WidgetBean(
  @JvmField val widget: StatusBarWidget,
  @JvmField val position: Position,
  @JvmField val component: JComponent,
  override val order: LoadingOrder,
) : Orderable {
  val anchor: String
    get() = order.toString()

  override val orderId: String
    get() = widget.ID()

  override fun toString(): String = "Widget(id=$orderId, order=$order, position=$position)"
}

/**
 * Registry for status bar widgets. Stores widgets in insertion order.
 */
internal class WidgetRegistry {
  private val widgetMap = LinkedHashMap<String, WidgetBean>()

  // Queries
  fun get(id: String): WidgetBean? = widgetMap.get(id)
  fun getWidget(id: String): StatusBarWidget? = widgetMap.get(id)?.widget
  fun getComponent(id: String): JComponent? = widgetMap.get(id)?.component
  fun getAnchor(id: String): String? = widgetMap.get(id)?.anchor
  fun getAllWidgets(): Collection<StatusBarWidget> = widgetMap.values.map { it.widget }
  fun getAllBeans(): List<WidgetBean> = widgetMap.values.toList()
  fun containsKey(id: String): Boolean = widgetMap.containsKey(id)
  val size: Int get() = widgetMap.size

  // Mutations
  fun put(id: String, bean: WidgetBean) {
    widgetMap.put(id, bean)
  }

  fun remove(id: String): WidgetBean? = widgetMap.remove(id)

  // Filtering for sort
  fun filterByPosition(position: Position): MutableList<Orderable> {
    val result = mutableListOf<Orderable>()
    widgetMap.values.filterTo(result) { it.position == position }
    return result
  }
}
