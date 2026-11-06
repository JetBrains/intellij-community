// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UI
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.EnableTracingFor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Cursor
import java.awt.Graphics2D
import javax.swing.JRootPane
import javax.swing.SwingUtilities

// Trace logging makes the tests also run the logging code of the override.
@TestApplication
@EnableTracingFor(categories = ["#com.intellij.openapi.wm.impl.GlassPaneCursorOverride"])
class IdeGlassPaneImplCursorOverrideTest {
  @Test
  fun `an override makes the pane visible and enabled with the cursor`(): Unit = timeoutRunBlocking {
    val glassPane = withContext(Dispatchers.UI) { IdeGlassPaneImpl(JRootPane()) }
    val requestor = Any()

    val withOverride = withContext(Dispatchers.UI) {
      glassPane.setCursorOverride(cursor(Cursor.E_RESIZE_CURSOR), requestor)
      glassPane.state()
    }
    assertThat(withOverride).isEqualTo(PaneState(visible = true, enabled = true, cursorType = Cursor.E_RESIZE_CURSOR))

    val withoutOverride = withContext(Dispatchers.UI) {
      glassPane.setCursorOverride(null, requestor)
      glassPane.state()
    }
    assertThat(withoutOverride).isEqualTo(PaneState(visible = false, enabled = false, cursorType = null))
  }

  @Test
  fun `the latest request wins`(): Unit = timeoutRunBlocking {
    val glassPane = withContext(Dispatchers.UI) { IdeGlassPaneImpl(JRootPane()) }
    val first = Any()
    val second = Any()

    val cursorTypes = withContext(Dispatchers.UI) {
      val result = ArrayList<Int?>()
      glassPane.setCursorOverride(cursor(Cursor.E_RESIZE_CURSOR), first)
      glassPane.setCursorOverride(cursor(Cursor.S_RESIZE_CURSOR), second)
      result.add(glassPane.state().cursorType)
      glassPane.setCursorOverride(cursor(Cursor.W_RESIZE_CURSOR), first)
      result.add(glassPane.state().cursorType)
      glassPane.setCursorOverride(null, first)
      result.add(glassPane.state().cursorType)
      result
    }
    assertThat(cursorTypes).containsExactly(Cursor.S_RESIZE_CURSOR, Cursor.W_RESIZE_CURSOR, Cursor.S_RESIZE_CURSOR)

    val withoutOverride = withContext(Dispatchers.UI) {
      glassPane.setCursorOverride(null, second)
      glassPane.state()
    }
    assertThat(withoutOverride).isEqualTo(PaneState(visible = false, enabled = false, cursorType = null))
  }

  @Test
  fun `the cursor of the pane comes back after the override`(): Unit = timeoutRunBlocking {
    val glassPane = withContext(Dispatchers.UI) {
      IdeGlassPaneImpl(JRootPane()).also { it.cursor = cursor(Cursor.HAND_CURSOR) }
    }
    val requestor = Any()

    val withoutOverride = withContext(Dispatchers.UI) {
      glassPane.setCursorOverride(cursor(Cursor.E_RESIZE_CURSOR), requestor)
      glassPane.setCursorOverride(null, requestor)
      glassPane.state()
    }
    assertThat(withoutOverride).isEqualTo(PaneState(visible = false, enabled = false, cursorType = Cursor.HAND_CURSOR))
  }

  // A headless test cannot create a window, so it checks only that an ancestor that is not a window keeps its cursor.
  @Test
  fun `an override does not change the cursor of an ancestor that is not a window`(): Unit = timeoutRunBlocking {
    val rootPane = withContext(Dispatchers.UI) { JRootPane() }
    val glassPane = withContext(Dispatchers.UI) {
      IdeGlassPaneImpl(rootPane).also { rootPane.glassPane = it }
    }
    val requestor = Any()

    val withOverride = withContext(Dispatchers.UI) {
      glassPane.setCursorOverride(cursor(Cursor.SE_RESIZE_CURSOR), requestor)
      rootPane.cursorTypeOrNull()
    }
    assertThat(withOverride).isNull()

    withContext(Dispatchers.UI) {
      glassPane.setCursorOverride(null, requestor)
    }
  }

  @Test
  fun `a painter keeps the pane visible after the override`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val glassPane = withContext(Dispatchers.UI) { IdeGlassPaneImpl(JRootPane()) }
    val requestor = Any()
    val painterDisposable = Disposer.newDisposable(disposable)

    val withPainter = withContext(Dispatchers.UI) {
      glassPane.setCursorOverride(cursor(Cursor.E_RESIZE_CURSOR), requestor)
      glassPane.addPainter(null, EmptyPainter(), painterDisposable)
      glassPane.setCursorOverride(null, requestor)
      glassPane.state()
    }
    assertThat(withPainter).isEqualTo(PaneState(visible = true, enabled = false, cursorType = null))

    // The pane removes the painter in a raw `invokeLater` event, so wait for an event that comes after it.
    val painterRemoved = CompletableDeferred<Unit>()
    withContext(Dispatchers.UI) {
      Disposer.dispose(painterDisposable)
      SwingUtilities.invokeLater { painterRemoved.complete(Unit) }
    }
    painterRemoved.await()
    val withoutPainter = withContext(Dispatchers.UI) { glassPane.state() }
    assertThat(withoutPainter).isEqualTo(PaneState(visible = false, enabled = false, cursorType = null))
  }
}

/**
 * The observable state of a glass pane; [cursorType] is `null` if the pane has no cursor of its own.
 */
private data class PaneState(val visible: Boolean, val enabled: Boolean, val cursorType: Int?)

private fun IdeGlassPaneImpl.state(): PaneState = PaneState(isVisible, isEnabled, cursorTypeOrNull())

/**
 * The type of the cursor of this component, or `null` if the component has no cursor of its own.
 */
private fun Component.cursorTypeOrNull(): Int? = if (isCursorSet) cursor.type else null

private fun cursor(type: Int): Cursor = Cursor.getPredefinedCursor(type)

private class EmptyPainter : AbstractPainter() {
  override fun executePaint(component: Component, g: Graphics2D) {}
}
