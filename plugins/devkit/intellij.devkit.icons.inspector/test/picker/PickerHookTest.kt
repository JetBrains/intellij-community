// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.picker

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.Painter
import com.intellij.openapi.wm.IdeGlassPane
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Cursor
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import javax.swing.JComponent
import javax.swing.JRootPane

/** Which hook the picker uses for a window, and where its highlight goes (no IDE needed). */
class PickerHookTest {
  private class FakeIdeGlassPane : JComponent(), IdeGlassPane {
    override fun addMousePreprocessor(listener: MouseListener, parent: Disposable) {}
    override fun addMouseListener(listener: MouseListener, coroutineScope: CoroutineScope) {}
    override fun addMouseMotionPreprocessor(listener: MouseMotionListener, parent: Disposable) {}
    override fun addPainter(component: Component?, painter: Painter, parent: Disposable) {}
    override fun setCursor(cursor: Cursor?, requestor: Any) {}
  }

  @Test
  fun `window with an IdeGlassPane uses glass-pane preprocessors and overlay`() {
    val rootPane = JRootPane()
    val glassPane = FakeIdeGlassPane()
    rootPane.glassPane = glassPane
    assertEquals(PickerHook.GLASS_PANE, pickerHookFor(rootPane))
    assertSame(glassPane, overlayHostFor(rootPane))
  }

  @Test
  fun `window without an IdeGlassPane falls back to the event queue and the layered pane`() {
    val rootPane = JRootPane() // plain JDialog/JWindow/heavyweight popup root: default JPanel glass pane
    assertEquals(PickerHook.EVENT_QUEUE, pickerHookFor(rootPane))
    assertSame(rootPane.layeredPane, overlayHostFor(rootPane))
  }

  @Test
  fun `non-Swing window falls back to the event queue`() {
    assertEquals(PickerHook.EVENT_QUEUE, pickerHookFor(null))
  }
}
