// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Where the inspector UI goes for a pick: tool window, or a window owned by the modal dialog (headless, fake windows). */
class ResultsHostSelectionTest {
  private class FakeWindow(val name: String, val owner: FakeWindow? = null, val modal: Boolean = false, val showing: Boolean = true) {
    override fun toString() = name
  }

  private fun select(start: FakeWindow?, mode: InspectorMode = InspectorMode.DOCKED, modalExclusionSupported: Boolean = true) =
    ResultsHostSelection.select(start, mode, modalExclusionSupported, { it.owner }, { it.modal && it.showing })

  private val frame = FakeWindow("frame")

  @Test
  fun `pick in the IDE frame goes to the tool window`() {
    assertEquals(ResultsHost.ToolWindow, select(frame))
    assertEquals(ResultsHost.ToolWindow, select(null))
  }

  @Test
  fun `pick in a non-modal dialog goes to the tool window`() {
    val showcase = FakeWindow("ComposeShowcase (MODELESS)", owner = frame)
    assertEquals(ResultsHost.ToolWindow, select(showcase))
  }

  @Test
  fun `pick in a modal dialog opens a window owned by it`() {
    val dialog = FakeWindow("Jewel showcase", owner = frame, modal = true)
    assertEquals(ResultsHost.OwnedWindow(dialog), select(dialog))
  }

  @Test
  fun `pick in a popup or the inspector window owned by a modal dialog reuses that dialog`() {
    val dialog = FakeWindow("wizard", owner = frame, modal = true)
    val popup = FakeWindow("combo popup", owner = dialog)
    val inspector = FakeWindow("inspector window", owner = dialog)
    assertEquals(ResultsHost.OwnedWindow(dialog), select(popup))
    assertEquals(ResultsHost.OwnedWindow(dialog), select(inspector))
  }

  @Test
  fun `nested modal dialogs pick the nearest one`() {
    val outer = FakeWindow("settings", owner = frame, modal = true)
    val inner = FakeWindow("nested", owner = outer, modal = true)
    assertEquals(ResultsHost.OwnedWindow(inner), select(inner))
  }

  @Test
  fun `hidden modal dialogs are ignored`() {
    val closed = FakeWindow("closed", owner = frame, modal = true, showing = false)
    val child = FakeWindow("child", owner = closed)
    assertEquals(ResultsHost.ToolWindow, select(child))
  }

  @Test
  fun `owner cycles terminate`() {
    val a = object {
      var owner: Any? = null
    }
    a.owner = a
    assertEquals(ResultsHost.ToolWindow, ResultsHostSelection.select<Any>(a, InspectorMode.DOCKED, true, { a.owner }, { false }))
  }

  @Test
  fun `detached mode routes every pick to the ownerless window when modal exclusion is supported`() {
    val dialog = FakeWindow("Jewel showcase", owner = frame, modal = true)
    val nested = FakeWindow("nested", owner = dialog, modal = true)
    for (start in listOf(frame, dialog, nested, null)) {
      assertEquals(ResultsHost.DetachedWindow<FakeWindow>(null), select(start, InspectorMode.DETACHED))
    }
  }

  @Test
  fun `detached mode without modal exclusion moves the window into the nearest modal dialog`() {
    val dialog = FakeWindow("settings", owner = frame, modal = true)
    val nested = FakeWindow("nested", owner = dialog, modal = true)
    val popup = FakeWindow("combo popup", owner = nested)
    assertEquals(ResultsHost.DetachedWindow<FakeWindow>(null), select(frame, InspectorMode.DETACHED, modalExclusionSupported = false))
    assertEquals(ResultsHost.DetachedWindow(dialog), select(dialog, InspectorMode.DETACHED, modalExclusionSupported = false))
    assertEquals(ResultsHost.DetachedWindow(nested), select(popup, InspectorMode.DETACHED, modalExclusionSupported = false))
  }

  @Test
  fun `docked mode ignores modal exclusion`() {
    val dialog = FakeWindow("wizard", owner = frame, modal = true)
    assertEquals(ResultsHost.OwnedWindow(dialog), select(dialog, InspectorMode.DOCKED, modalExclusionSupported = true))
    assertEquals(ResultsHost.ToolWindow, select(frame, InspectorMode.DOCKED, modalExclusionSupported = false))
  }
}
