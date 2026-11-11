// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.ClientProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JComponent
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import kotlin.test.assertEquals

@TestApplication
class OneUnitPerWheelNotchTest {
  @Test
  fun `a wheel notch scrolls the system count of rows without the property`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val scrollPane = createScrollPane()
      scrollPane.dispatchEvent(wheelNotch(scrollPane))
      assertEquals(SYSTEM_SCROLL_AMOUNT * ROW_HEIGHT, scrollPane.viewport.viewPosition.y)
    }
  }

  @Test
  fun `a wheel notch scrolls one row with the property`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val scrollPane = createScrollPane()
      ClientProperty.put(scrollPane.viewport.view as JComponent, JBScrollPane.ONE_UNIT_PER_WHEEL_NOTCH, true)
      scrollPane.dispatchEvent(wheelNotch(scrollPane))
      assertEquals(ROW_HEIGHT, scrollPane.viewport.viewPosition.y)
    }
  }

  @Test
  fun `the scroll bar computes one row per notch from the property, not from the event`(): Unit = timeoutRunBlocking {
    assumeFalse(SystemInfo.isMac, "On macOS the pixel-perfect delta comes from the native wheel rotation")
    withContext(Dispatchers.EDT) {
      val scrollBar = DeltaScrollBar()
      val scrollPane = createScrollPane(scrollBar)
      ClientProperty.put(scrollPane.viewport.view as JComponent, JBScrollPane.ONE_UNIT_PER_WHEEL_NOTCH, true)
      assertEquals(ROW_HEIGHT.toDouble(), scrollBar.delta(wheelNotch(scrollPane)))
    }
  }
}

private class DeltaScrollBar : JBScrollBar() {
  fun delta(event: MouseWheelEvent): Double = getDeltaAdjusted(event)
}

private const val ROW_HEIGHT = 50
private const val SYSTEM_SCROLL_AMOUNT = 3

private fun createScrollPane(verticalScrollBar: JScrollBar? = null): JBScrollPane {
  val list = JBList((1..20).toList())
  list.fixedCellHeight = ROW_HEIGHT
  list.fixedCellWidth = 100
  val scrollPane = JBScrollPane(list)
  if (verticalScrollBar != null) {
    scrollPane.verticalScrollBar = verticalScrollBar
  }
  scrollPane.setSize(200, 200)
  scrollPane.doLayout()
  scrollPane.viewport.doLayout()
  return scrollPane
}

private fun wheelNotch(scrollPane: JScrollPane): MouseWheelEvent =
  MouseWheelEvent(scrollPane, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, 10, 10, 0, 0, 0, false,
                  MouseWheelEvent.WHEEL_UNIT_SCROLL, SYSTEM_SCROLL_AMOUNT, 1, 1.0)
