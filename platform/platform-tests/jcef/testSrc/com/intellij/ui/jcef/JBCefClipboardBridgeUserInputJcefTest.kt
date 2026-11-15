// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.ui.scale.TestScaleHelper
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.awt.BorderLayout
import java.awt.datatransfer.DataFlavor
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities

private val REFUSED = "fail ${JBCefJSQuery.ERR_CODE_FRAME_REFUSED}"

/**
 * Tests the copies without a token of the IDE that the clipboard bridge takes from the frames after a user input,
 * in real JCEF.
 *
 * The page has an input and three iframes: one of the origin of the page, one of another origin, and one of the origin
 * of the page without the permission to write the clipboard. Each frame reports the answer of the bridge to the server.
 *
 * In a test run, Chromium does not take the mouse events that the test sends, neither from the component of the browser
 * nor from DevTools. So a click of the test has three parts: the mouse press and the release that the component gets,
 * as the IDE passes them to the browser; a user gesture of DevTools in the main frame, which Chromium also gives to the
 * frames of the origin of the page; and the focus, which a script of the main frame moves into the clicked frame. The
 * mouse press can take the focus of Chromium from the browser in a window of the test that stays inactive, so the test
 * gives it back, and a `navigator.clipboard` write waits for the focus of its document, which Chromium requires.
 *
 * The test runs only with `-Dintellij.test.standalone=true`, `-Djava.awt.headless=false` and native JCEF.
 */
class JBCefClipboardBridgeUserInputJcefTest {
  companion object {
    @ClassRule
    @JvmField
    val appRule: ApplicationRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val disposableRule: DisposableRule = DisposableRule()

  private lateinit var server: TestPageServer
  private lateinit var browser: JBCefBrowser
  private lateinit var fn: String
  private val copies = ConcurrentLinkedQueue<String>()

  @Before
  fun before() {
    TestScaleHelper.assumeStandalone()
    TestScaleHelper.assumeHeadful()
    server = TestPageServer()
    CopyPasteManager.getInstance().addContentChangedListener({ _, newTransferable ->
      if (newTransferable != null && newTransferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        copies.add(newTransferable.getTransferData(DataFlavor.stringFlavor) as String)
      }
    }, disposableRule.disposable)
    openPage()
  }

  @After
  fun after() {
    if (::server.isInitialized) server.close()
  }

  @Test
  fun `a navigator clipboard write of an iframe of the page origin reaches the IDE clipboard after a click into it`() {
    click("same")
    assertEquals("resolved", navigatorWrite("same", "same-origin-write"))
    waitForCopy("same-origin-write")
  }

  @Test
  fun `an iframe of another origin cannot write after a click into it`() {
    click("cross")
    assertEquals(REFUSED, copyRequest("cross", "cross-write"))
  }

  @Test
  fun `an iframe of another origin cannot write after it takes the focus`() {
    click(null)
    focus("cross")
    assertEquals(REFUSED, copyRequest("cross", "focus-write"))
  }

  @Test
  fun `one click lets an iframe of the page origin write once`() {
    click("same")
    assertEquals("ok", copyRequest("same", "first-write"))
    assertEquals(REFUSED, copyRequest("same", "second-write"))
    waitForCopy("first-write")
    assertEquals(listOf("first-write"), copies.toList())
  }

  @Test
  fun `an iframe of the page origin cannot write without a recent user input`() {
    var now = System.nanoTime()
    browser.clipboardBridge!!.setClockInTests { now }
    focus("same")
    assertEquals(REFUSED, copyRequest("same", "no-input-write"))
    click("same")
    now += TimeUnit.MILLISECONDS.toNanos(1001)
    assertEquals(REFUSED, copyRequest("same", "late-write"))
  }

  @Test
  fun `the script of an iframe sends a write only after Chromium completes it`() {
    click("denied")
    // Chromium refuses the write for the permission of the iframe, not for the focus of its document.
    val result = navigatorWrite("denied", "denied-write")
    assertTrue(result, result.startsWith("rejected NotAllowedError") && !result.contains("not focused"))
    // The click can still give one write, because the rejected write sent no request.
    assertEquals("ok", copyRequest("denied", "after-rejected-write"))
    waitForCopy("after-rejected-write")
    assertFalse(copies.contains("denied-write"))
  }

  @Test
  fun `the main frame writes after a click`() {
    click(null)
    assertEquals("ok", copyRequest(null, "main-write"))
  }

  private fun openPage() {
    @Language("HTML")
    val main = """
      <!DOCTYPE html>
      <html>
      <body>
      <script>
        function report(frame, fn, result) {
          fetch('/report?' + encodeURIComponent(frame + ' ' + fn + ' ' + result), {mode: 'no-cors'});
        }
        const NAME = 'input-main';
        report(NAME, 'loaded', 'ok');
      </script>
      <input id="main-input">
      <iframe name="same" src="/input-frame?name=input-same"></iframe>
      <iframe name="cross" src="${server.otherOrigin}/input-frame?name=input-cross"></iframe>
      <iframe name="denied" allow="clipboard-write 'none'" src="/input-frame?name=input-denied"></iframe>
      </body>
      </html>
    """.trimIndent()
    server.serve("/input", main)
    server.serveSubframe("input", "report(NAME, 'loaded', 'ok');")

    browser = JBCefBrowser.createBuilder().setOffScreenRendering(true).setUseClipboardBridge(true).build()
    Disposer.register(disposableRule.disposable, browser)
    fn = browser.clipboardBridge!!.queryFuncName
    SwingUtilities.invokeAndWait {
      val frame = JFrame(JBCefClipboardBridgeUserInputJcefTest::class.java.simpleName)
      frame.setSize(820, 640)
      frame.add(browser.component, BorderLayout.CENTER)
      frame.isVisible = true
      Disposer.register(disposableRule.disposable) { frame.dispose() }
      browser.loadURL("${server.mainOrigin}/input")
    }
    server.awaitResults(setOf("main", "same", "cross", "denied").map { "input-$it" to "loaded" }.toSet())
    // The window of the test may stay inactive, so the browser gets the focus that an active window gives it.
    SwingUtilities.invokeAndWait { browser.cefBrowser.setFocus(true) }
  }

  // Clicks the iframe with the name, or the input of the main frame, as the class comment describes.
  private fun click(frameName: String?) {
    SwingUtilities.invokeAndWait {
      val component = browser.cefBrowser.uiComponent
      for ((id, modifiers) in listOf(MouseEvent.MOUSE_PRESSED to InputEvent.BUTTON1_DOWN_MASK, MouseEvent.MOUSE_RELEASED to 0)) {
        component.dispatchEvent(MouseEvent(component, id, System.currentTimeMillis(), modifiers, 10, 10, 1, false, MouseEvent.BUTTON1))
      }
    }
    // The mouse press moves the focus of AWT, and in a window of the test that stays inactive the browser can lose the
    // focus of Chromium with it: give the browser the focus again, as an active window gives it.
    SwingUtilities.invokeAndWait {
      UIUtil.dispatchAllInvocationEvents()
      browser.cefBrowser.setFocus(true)
    }
    browser.cefBrowser.devToolsClient
      .executeDevToolsMethod("Runtime.evaluate", "{\"expression\": \"0\", \"userGesture\": true}")
      .get(10, TimeUnit.SECONDS)
    if (frameName == null) {
      run(null, "document.getElementById('main-input').focus();")
      waitFor("the focus in the main frame") { browser.cefBrowser.focusedFrame?.isMain == true }
    }
    else {
      focus(frameName)
    }
  }

  // Writes the text with the clipboard API of the iframe once its document has the focus, which Chromium requires, and
  // returns the result: "resolved", or "rejected" with the name and the message of the error.
  private fun navigatorWrite(frameName: String, text: String): String {
    run(frameName, "(function write(deadline) { " +
                   "if (!document.hasFocus() && Date.now() < deadline) { setTimeout(function() { write(deadline); }, 20); return; } " +
                   "navigator.clipboard.writeText('$text').then(function() { report(NAME, 'write', 'resolved'); }, " +
                   "function(e) { report(NAME, 'write', 'rejected ' + e.name + ': ' + e.message); }); })(Date.now() + 3000);")
    return server.awaitResults(setOf("input-$frameName" to "write")).values.single()
  }

  // Moves the focus into the iframe with a script of the main frame.
  private fun focus(frameName: String) {
    run(null, "document.getElementsByName('$frameName')[0].focus();")
    waitFor("the focus in $frameName") { browser.cefBrowser.focusedFrame?.url?.contains("input-$frameName") == true }
  }

  // Runs the script in the frame with the name, or in the main frame.
  private fun run(frameName: String?, script: String) {
    val frame = if (frameName == null) browser.cefBrowser.mainFrame else browser.cefBrowser.getFrameByName(frameName)
    frame.executeJavaScript(script, frame.url, 0)
  }

  // Sends a copy request without a token from the frame, as the script of the bridge does, and returns the answer.
  private fun copyRequest(frameName: String?, text: String): String {
    run(frameName, """
      window['$fn']({request: 'copy::' + encodeURIComponent('$text') + '\n',
                     onSuccess: function(r) { report(NAME, '$text', 'ok'); },
                     onFailure: function(code, message) { report(NAME, '$text', 'fail ' + code); }});
    """.trimIndent())
    return server.awaitResults(setOf("input-${frameName ?: "main"}" to text)).values.single()
  }

  private fun waitForCopy(text: String) {
    waitFor("'$text' in the IDE clipboard, got $copies") {
      SwingUtilities.invokeAndWait { UIUtil.dispatchAllInvocationEvents() }
      copies.contains(text)
    }
  }
}
