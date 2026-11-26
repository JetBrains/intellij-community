// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.awt.Rectangle

/** The detached mode has exactly one inspector window, also for picks in several (nested) modal dialogs (headless, fakes). */
class DetachedWindowSlotTest {
  private class FakeWindow(val name: String, val owner: FakeWindow? = null, val modal: Boolean = false) {
    var showing = true

    override fun toString() = name
  }

  private class FakeHost(val owner: FakeWindow?, var bounds: Rectangle?) {
    var open = true
    var frontCount = 0
  }

  private class FakeHosts : DetachedWindowSlot.Hosts<FakeWindow, FakeHost> {
    val opened = mutableListOf<FakeHost>()

    val alive: List<FakeHost>
      get() = opened.filter { it.open }

    override fun open(owner: FakeWindow?, bounds: Rectangle?): FakeHost {
      assertTrue(alive.isEmpty(), "a second inspector window opens while $alive is open")
      return FakeHost(owner, bounds ?: Rectangle(10, 10, 900, 640)).also { opened += it }
    }

    override fun close(host: FakeHost) {
      host.open = false
    }

    override fun bounds(host: FakeHost): Rectangle? = host.bounds

    override fun toFront(host: FakeHost) {
      host.frontCount++
    }
  }

  private val hosts = FakeHosts()
  private val slot = DetachedWindowSlot(hosts)
  private val frame = FakeWindow("frame")

  private fun pick(start: FakeWindow?, modalExclusionSupported: Boolean) {
    val host = ResultsHostSelection.select(start, InspectorMode.DETACHED, modalExclusionSupported, { it.owner }, { it.modal && it.showing })
    when (host) {
      is ResultsHost.DetachedWindow -> slot.show(host.owner)
      ResultsHost.ToolWindow, is ResultsHost.OwnedWindow -> fail("detached mode routed a pick in $start to $host")
    }
    assertEquals(1, hosts.alive.size, "inspector windows after a pick in $start")
  }

  private fun closeDialog(dialog: FakeWindow) {
    dialog.showing = false
    slot.ownerGone(dialog, ResultsHostSelection.modalOwner(dialog.owner, { it.owner }, { it.modal && it.showing }))
  }

  @Test
  fun `with modal exclusion, picks in several and nested modal dialogs reuse the one window`() {
    val showcase = FakeWindow("Jewel showcase", owner = frame, modal = true)
    val nested = FakeWindow("nested", owner = showcase, modal = true)
    val popup = FakeWindow("combo popup", owner = nested)
    val settings = FakeWindow("settings", owner = frame, modal = true)

    pick(frame, modalExclusionSupported = true)
    val window = hosts.opened.single()
    pick(showcase, modalExclusionSupported = true)
    pick(nested, modalExclusionSupported = true)
    pick(popup, modalExclusionSupported = true)
    closeDialog(nested)
    closeDialog(showcase)
    pick(settings, modalExclusionSupported = true)
    pick(null, modalExclusionSupported = true)

    assertSame(window, hosts.opened.single())
    assertSame(window, slot.current)
    assertNull(window.owner)
    assertTrue(window.open)
    assertEquals(5, window.frontCount)
  }

  @Test
  fun `without modal exclusion, the one window moves between modal dialogs and never doubles`() {
    val outer = FakeWindow("settings", owner = frame, modal = true)
    val inner = FakeWindow("nested", owner = outer, modal = true)
    val other = FakeWindow("wizard", owner = frame, modal = true)

    pick(frame, modalExclusionSupported = false)
    hosts.opened.single().bounds = Rectangle(100, 200, 800, 600)
    pick(outer, modalExclusionSupported = false)
    pick(outer, modalExclusionSupported = false)
    pick(inner, modalExclusionSupported = false)
    assertSame(inner, slot.currentOwner)

    closeDialog(inner)
    assertSame(outer, slot.currentOwner)
    closeDialog(outer)
    assertNull(slot.currentOwner)
    pick(other, modalExclusionSupported = false)
    assertSame(other, slot.currentOwner)

    assertEquals(listOf(null, outer, inner, outer, null, other), hosts.opened.map { it.owner })
    assertEquals(1, hosts.alive.size)
    // the window keeps its bounds when it moves
    assertTrue(hosts.opened.all { it.bounds == Rectangle(100, 200, 800, 600) })
    // the second pick in the same dialog reused the window
    assertEquals(1, hosts.opened[1].frontCount)
  }

  @Test
  fun `a dialog that does not own the window does not move it`() {
    val dialog = FakeWindow("dialog", owner = frame, modal = true)
    pick(frame, modalExclusionSupported = false)
    closeDialog(dialog)
    assertSame(hosts.opened.single(), slot.current)
  }

  @Test
  fun `a window closed by the user is forgotten, and the next pick opens one again`() {
    pick(frame, modalExclusionSupported = true)
    val closed = hosts.opened.single()
    closed.open = false
    assertTrue(slot.forget(closed))
    assertNull(slot.current)
    // the slot closes hosts itself: a late notification for that host changes nothing
    assertEquals(false, slot.forget(closed))

    pick(frame, modalExclusionSupported = true)
    assertEquals(2, hosts.opened.size)
  }

  @Test
  fun `close closes the window and a host closed by the slot is no longer current`() {
    val dialog = FakeWindow("dialog", owner = frame, modal = true)
    pick(dialog, modalExclusionSupported = false)
    val window = slot.current!!
    slot.close()
    assertEquals(false, window.open)
    assertNull(slot.current)
    assertEquals(false, slot.forget(window))
    slot.ownerGone(dialog, null)
    assertTrue(hosts.alive.isEmpty())
  }
}
