// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.ui.jcef.JBCefClipboardBridgeRules.Command
import com.intellij.ui.jcef.JBCefClipboardBridgeRules.Frame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

private const val PAGE = "https://page.test/index.html"

/**
 * Tests the requests that the clipboard bridge accepts, with a fake clock and without JCEF.
 */
class JBCefClipboardBridgeRulesTest {
  private var now = 1_000_000_000L
  private val rules = JBCefClipboardBridgeRules { now }

  private fun advance(milliseconds: Long) {
    now += TimeUnit.MILLISECONDS.toNanos(milliseconds)
  }

  @Test
  fun `a copy or a cut of the IDE in a subframe gives one copy with its token within 5 seconds`() {
    val frame = subframe("frame", "https://other.test/")
    val token = rules.startCopy("frame")
    assertFalse(rules.acceptsCopy(frame, "another-token"))
    assertFalse(rules.acceptsCopy(subframe("another-frame", "https://other.test/"), token))
    assertTrue(rules.acceptsCopy(frame, token))
    assertFalse(rules.acceptsCopy(frame, token))

    val late = rules.startCopy("frame")
    advance(5001)
    assertFalse(rules.acceptsCopy(frame, late))
  }

  @Test
  fun `the answer of the frame to a copy of the IDE gives its command once, for its frame and token, within 5 seconds`() {
    val token = rules.startCopy("frame", Command.CUT)
    assertTrue(rules.isCopyWaitingForAnswer())
    assertNull(rules.takeCopyReady("another-frame", token))
    assertNull(rules.takeCopyReady("frame", "another-token"))
    assertEquals(Command.CUT, rules.takeCopyReady("frame", token))
    assertFalse(rules.isCopyWaitingForAnswer())
    assertNull(rules.takeCopyReady("frame", token), "a second answer")
    // The token stays valid for the copy request of the command.
    assertTrue(rules.acceptsCopy(subframe("frame", "https://other.test/"), token))
    assertNull(rules.takeCopyReady("frame", token))

    val late = rules.startCopy("frame")
    advance(5001)
    assertNull(rules.takeCopyReady("frame", late))
    assertFalse(rules.isCopyWaitingForAnswer())
  }

  @Test
  fun `a copy request with a token that the bridge does not accept counts as one without a token`() {
    val stale = rules.startCopy("frame")
    // Another copy of the IDE replaces the one of the stale token.
    rules.startCopy("another-frame")
    assertTrue(rules.acceptsCopy(mainFrame(hasFocus = true), stale))
    assertFalse(rules.acceptsCopy(subframe("frame", "https://other.test/"), stale))
    rules.userInput()
    assertTrue(rules.acceptsCopy(subframe("frame", "https://page.test/frame.html"), stale))
  }

  @Test
  fun `the main frame copies when the browser has the focus or within 5 seconds after a user input`() {
    // A browser with a source of user input, but no input yet.
    assertFalse(rules.acceptsCopy(mainFrame(hasFocus = false), ""))
    assertTrue(rules.acceptsCopy(mainFrame(hasFocus = true), ""))
    rules.userInput()
    assertTrue(rules.acceptsCopy(mainFrame(hasFocus = false), ""))
    // The window of the main frame is longer than the one of a subframe, and its copies do not use up the input.
    advance(4900)
    assertTrue(rules.acceptsCopy(mainFrame(hasFocus = false), ""))
    advance(101)
    assertFalse(rules.acceptsCopy(mainFrame(hasFocus = false), ""))
  }

  @Test
  fun `the main frame of a browser without a source of user input copies at any time, and its subframes do not`() {
    assertTrue(rules.acceptsCopy(mainFrame(hasFocus = false, hasInputSource = false), ""))
    advance(60_000)
    assertTrue(rules.acceptsCopy(mainFrame(hasFocus = false, hasInputSource = false), ""))
    assertFalse(rules.acceptsCopy(Frame("frame", false, "https://page.test/frame.html", PAGE, "frame", false, false), ""))
  }

  @Test
  fun `the focus of a browser is in its component, or in its UI component when it has no component`() {
    val uiComponent = JPanel()
    val focusOwner = JPanel()
    uiComponent.add(focusOwner)
    assertTrue(JBCefClipboardBridgeRules.isFocusIn(focusOwner, null, uiComponent))
    assertTrue(JBCefClipboardBridgeRules.isFocusIn(focusOwner, uiComponent, null))
    assertFalse(JBCefClipboardBridgeRules.isFocusIn(focusOwner, JPanel(), uiComponent))
    assertFalse(JBCefClipboardBridgeRules.isFocusIn(null, null, uiComponent))
    assertFalse(JBCefClipboardBridgeRules.isFocusIn(focusOwner, null, null))
  }

  @Test
  fun `a focused subframe of the origin of the main frame writes once within a second after a user input`() {
    val frame = subframe("frame", "https://page.test/frame.html")
    assertFalse(rules.acceptsCopy(frame, ""))
    rules.userInput()
    advance(900)
    assertTrue(rules.acceptsCopy(frame, ""))
    assertFalse(rules.acceptsCopy(frame, ""))
    rules.userInput()
    advance(1001)
    assertFalse(rules.acceptsCopy(frame, ""))
  }

  @Test
  fun `a subframe of another origin, an unfocused subframe and a file subframe never write without a token`() {
    rules.userInput()
    assertFalse(rules.acceptsCopy(subframe("frame", "https://other.test/"), ""))
    assertFalse(rules.acceptsCopy(subframe("frame", "about:srcdoc"), ""))
    assertFalse(rules.acceptsCopy(subframe("frame", "https://page.test/", focusedFrameId = "main"), ""))
    assertFalse(rules.acceptsCopy(Frame("frame", false, "file:///a.html", "file:///b.html", "frame", true, true), ""))
    assertTrue(rules.acceptsCopy(subframe("frame", "https://page.test/"), ""))
  }

  @Test
  fun `a paste and a paste error need the token of the IDE paste within 5 seconds, once`() {
    val token = rules.startPaste("frame")
    assertFalse(rules.acceptsPaste("frame", "another-token"))
    assertFalse(rules.acceptsPaste("another-frame", token))
    assertTrue(rules.acceptsPaste("frame", token))
    assertFalse(rules.acceptsPaste("frame", token))

    val late = rules.startPaste("frame")
    advance(5001)
    assertFalse(rules.acceptsPaste("frame", late))
  }
}

private fun mainFrame(hasFocus: Boolean, hasInputSource: Boolean = true): Frame =
  Frame("main", true, PAGE, PAGE, "main", hasFocus, hasInputSource)

private fun subframe(id: String, url: String, focusedFrameId: String = id): Frame = Frame(id, false, url, PAGE, focusedFrameId, true, true)
