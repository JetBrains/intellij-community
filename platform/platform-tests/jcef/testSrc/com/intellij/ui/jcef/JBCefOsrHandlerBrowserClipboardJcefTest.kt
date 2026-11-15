// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.ui.scale.TestScaleHelper
import com.intellij.util.ui.UIUtil
import org.cef.browser.CefBrowser
import org.cef.handler.CefRenderHandlerAdapter
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.awt.Rectangle
import java.awt.datatransfer.DataFlavor
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import javax.swing.SwingUtilities

private val REFUSED = "fail ${JBCefJSQuery.ERR_CODE_FRAME_REFUSED}"

/**
 * Tests the clipboard bridge of a [JBCefOsrHandlerBrowser] in real JCEF. Such a browser has no component, and its owner
 * sends the input of the user to the native browser itself.
 *
 * The test runs only with `-Dintellij.test.standalone=true`, `-Djava.awt.headless=false` and native JCEF.
 */
class JBCefOsrHandlerBrowserClipboardJcefTest {
  companion object {
    @ClassRule
    @JvmField
    val appRule: ApplicationRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val disposableRule: DisposableRule = DisposableRule()

  private lateinit var server: TestPageServer
  private lateinit var browser: JBCefOsrHandlerBrowser
  private lateinit var fn: String
  private val copies = ConcurrentLinkedQueue<String>()
  // The component of the owner of the browser, the source of the events that the owner passes.
  private val owner = JPanel()
  // The clock of the rules of the bridge.
  @Volatile
  private var now = System.nanoTime()

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

    @Language("HTML")
    val page = """
      <!DOCTYPE html>
      <html>
      <body>
      <p id="text">osr-selection</p>
      <script>
        function report(frame, fn, result) {
          fetch('/report?' + encodeURIComponent(frame + ' ' + fn + ' ' + result), {mode: 'no-cors'});
        }
        const NAME = 'osr-main';
        report(NAME, 'loaded', 'ok');
      </script>
      </body>
      </html>
    """.trimIndent()
    server.serve("/osr", page)
  }

  // Creates the browser, as a session that sends the input itself does when `userInputForwarded`, and loads the page.
  private fun open(userInputForwarded: Boolean) {
    val renderHandler = object : CefRenderHandlerAdapter() {
      override fun getViewRect(browser: CefBrowser): Rectangle = Rectangle(0, 0, 400, 300)
    }
    browser = JBCefOsrHandlerBrowser.createWithClipboardBridgeInTests("${server.mainOrigin}/osr", renderHandler)
    Disposer.register(disposableRule.disposable, browser)
    if (userInputForwarded) browser.setUserInputForwarded()
    fn = browser.clipboardBridge!!.queryFuncName
    browser.clipboardBridge!!.setClockInTests { now }
    SwingUtilities.invokeAndWait { browser.createImmediately() }
    server.awaitResults(setOf("osr-main" to "loaded"))
  }

  @After
  fun after() {
    if (::server.isInitialized) server.close()
  }

  @Test
  fun `the main frame of a browser whose owner does not declare the input copies`() {
    open(userInputForwarded = false)
    assertEquals("ok", copyRequest("no-source-copy"))
    // An event that the owner passes does not declare a source.
    browser.notifyUserInput(mouseEvent(MouseEvent.MOUSE_MOVED))
    assertEquals("ok", copyRequest("event-copy"))
    waitForCopy("event-copy")
  }

  @Test
  fun `the main frame of a browser whose owner declares the input copies only within 5 seconds after an input`() {
    open(userInputForwarded = true)
    assertEquals(REFUSED, copyRequest("before-input-copy"))
    browser.notifyUserInput(mouseEvent(MouseEvent.MOUSE_MOVED))
    assertEquals(REFUSED, copyRequest("after-move-copy"))
    browser.notifyUserInput(mouseEvent(MouseEvent.MOUSE_PRESSED))
    assertEquals("ok", copyRequest("after-input-copy"))
    now += TimeUnit.MILLISECONDS.toNanos(4900)
    assertEquals("ok", copyRequest("delayed-copy"))
    now += TimeUnit.MILLISECONDS.toNanos(101)
    assertEquals(REFUSED, copyRequest("late-copy"))
    waitForCopy("delayed-copy")
    assertEquals(listOf("after-input-copy", "delayed-copy"), copies.toList())
  }

  @Test
  fun `the focus loss of the owner forgets the keys that are down`() {
    open(userInputForwarded = true)
    browser.notifyUserInput(keyEvent(KeyEvent.KEY_PRESSED))
    now += TimeUnit.SECONDS.toNanos(6)
    // The release of the key went to another component, so the next press looks like a repeated one.
    browser.notifyUserInput(keyEvent(KeyEvent.KEY_PRESSED))
    assertEquals(REFUSED, copyRequest("repeated-key-copy"))
    browser.notifyUserInputFocusLost()
    browser.notifyUserInput(keyEvent(KeyEvent.KEY_PRESSED))
    assertEquals("ok", copyRequest("after-focus-loss-copy"))
  }

  @Test
  fun `a copy that the IDE runs in the main frame needs no user input`() {
    open(userInputForwarded = true)
    browser.cefBrowser.executeJavaScript("document.getSelection().selectAllChildren(document.getElementById('text'));", browser.cefBrowser.url, 0)
    SwingUtilities.invokeAndWait {
      browser.cefBrowser.setFocus(true)
      browser.clipboardBridge!!.copy(browser.cefBrowser.mainFrame)
    }
    waitForCopy("osr-selection")
  }

  private fun keyEvent(id: Int): KeyEvent = KeyEvent(owner, id, System.currentTimeMillis(), 0, KeyEvent.VK_A, KeyEvent.CHAR_UNDEFINED)

  private fun mouseEvent(id: Int): MouseEvent =
    MouseEvent(owner, id, System.currentTimeMillis(), if (id == MouseEvent.MOUSE_PRESSED) InputEvent.BUTTON1_DOWN_MASK else 0, 10, 10, 1, false,
               MouseEvent.BUTTON1)

  // Sends a copy request without a token from the main frame, as the script of the bridge does, and returns the answer.
  private fun copyRequest(text: String): String {
    browser.cefBrowser.executeJavaScript("""
      window['$fn']({request: 'copy::' + encodeURIComponent('$text') + '\n',
                     onSuccess: function(r) { report(NAME, '$text', 'ok'); },
                     onFailure: function(code, message) { report(NAME, '$text', 'fail ' + code); }});
    """.trimIndent(), browser.cefBrowser.url, 0)
    return server.awaitResults(setOf("osr-main" to text)).values.single()
  }

  private fun waitForCopy(text: String) {
    waitFor("'$text' in the IDE clipboard, got $copies") {
      SwingUtilities.invokeAndWait { UIUtil.dispatchAllInvocationEvents() }
      copies.contains(text)
    }
  }
}
