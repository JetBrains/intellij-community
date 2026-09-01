// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.ide.minimap.settings.MinimapSettingsState
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.ComponentUtil
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import javax.swing.JPanel
import javax.swing.JScrollPane

/**
 * Guards the minimap *activation* path: given availability + the enabled setting + a supported file, the
 * [MinimapService] must actually attach a `MinimapPanel` to the editor (and must not when any precondition fails).
 * This catches the "minimap silently never appears" regression that the render/pure-logic tests cannot see.
 */
class MinimapActivationTest : BasePlatformTestCase() {
  private var originalState: MinimapSettingsState? = null

  override fun setUp() {
    super.setUp()
    originalState = MinimapSettings.getInstance().state.copy()
    setSettings(enabled = true)
  }

  override fun tearDown() {
    try {
      originalState?.let { MinimapSettings.getInstance().setState(it) }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  private fun setSettings(enabled: Boolean) {
    MinimapSettings.getInstance().setState(
      MinimapSettings.getInstance().state.copy(enabled = enabled, insideScrollbar = false)
    )
  }

  private fun openMainEditor(name: String, text: String): EditorImpl {
    val virtualFile = myFixture.configureByText(name, text).virtualFile
    val document = FileDocumentManager.getInstance().getDocument(virtualFile)!!
    val editor = EditorFactory.getInstance()
      .createEditor(document, project, virtualFile, false, EditorKind.MAIN_EDITOR) as EditorImpl
    Disposer.register(testRootDisposable) { EditorFactory.getInstance().releaseEditor(editor) }
    return editor
  }

  private fun openAndUpdate(name: String, text: String): EditorImpl {
    val editor = openMainEditor(name, text)
    MinimapService.getInstance().editorOpened(editor)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    return editor
  }

  fun testInstalledWhenAvailableEnabledAndSupported() {
    val editor = openAndUpdate("a.txt", "alpha\nbeta\ngamma\n")
    assertTrue(MinimapService.getInstance().isMinimapInstalled(editor))
  }

  fun testNotInstalledWhenDisabled() {
    setSettings(enabled = false)
    val editor = openAndUpdate("a.txt", "alpha\nbeta\n")
    assertFalse(MinimapService.getInstance().isMinimapInstalled(editor))
  }

  fun testWheelEventIsForwardedToEditorScrollPane() {
    val editor = openAndUpdate("a.txt", (0 until 100).joinToString("\n") { "line $it" })
    val minimap = (editor.component as JPanel).components.filterIsInstance<MinimapPanel>().single()
    val scrollPane = ComponentUtil.getParentOfType(JScrollPane::class.java, editor.contentComponent)!!
    var forwardedEvent: MouseWheelEvent? = null
    val recordingListener = MouseWheelListener { forwardedEvent = it }
    scrollPane.addMouseWheelListener(recordingListener)
    Disposer.register(testRootDisposable) { scrollPane.removeMouseWheelListener(recordingListener) }

    val event = MouseWheelEvent(
      minimap,
      MouseEvent.MOUSE_WHEEL,
      1,
      0,
      0,
      0,
      0,
      0,
      0,
      false,
      MouseWheelEvent.WHEEL_UNIT_SCROLL,
      3,
      1,
      0.25,
    )
    minimap.mouseWheelListeners.single().mouseWheelMoved(event)

    assertNotNull("the editor scroll pane must receive the wheel event", forwardedEvent)
    assertSame(scrollPane, forwardedEvent!!.source)
    assertEquals(event.preciseWheelRotation, forwardedEvent!!.preciseWheelRotation, 0.0)
    assertEquals(event.scrollAmount, forwardedEvent!!.scrollAmount)

    forwardedEvent = null
    val zeroDeltaEvent = MouseWheelEvent(
      minimap,
      MouseEvent.MOUSE_WHEEL,
      2,
      0,
      0,
      0,
      0,
      0,
      0,
      false,
      MouseWheelEvent.WHEEL_UNIT_SCROLL,
      0,
      0,
      0.0,
    )
    minimap.mouseWheelListeners.single().mouseWheelMoved(zeroDeltaEvent)

    assertNotNull("the editor scroll pane must receive a zero-delta wheel event", forwardedEvent)
  }
}
