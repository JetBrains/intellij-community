// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.ui.jcef.JBCefJSQuery.FramePolicy
import com.intellij.ui.jcef.menu.CefContextMenuCommands
import com.intellij.ui.scale.TestScaleHelper
import com.intellij.util.ref.GCWatcher
import com.intellij.util.ui.UIUtil
import com.sun.net.httpserver.HttpServer
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefMenuModel
import org.cef.callback.CefQueryCallback
import org.cef.callback.CefRunContextMenuCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.awt.BorderLayout
import java.awt.datatransfer.DataFlavor
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities

private const val REFUSED = "fail ${JBCefJSQuery.ERR_CODE_FRAME_REFUSED}"
// The answer of a frame that took the token of a copy of the IDE.
private const val COPY_READY = "copy-ready:"

/**
 * Tests the frame policy of [JBCefJSQuery] in real JCEF (IJPL-261493).
 * [JBCefJSQueryFramePolicyTest] tests the logic of the policies without JCEF.
 *
 * A local HTTP server serves a page on `http://127.0.0.1:<port>`. The page has a same-origin iframe,
 * and a sandboxed iframe of `http://localhost:<port>`, another origin. Each frame calls each query function
 * and sends the result to the server. The test runs only with `-Dintellij.test.standalone=true`,
 * `-Djava.awt.headless=false` and native JCEF, for example `-Djcef.native.bundle.path=<bundle>`.
 */
class JBCefJSQueryFramesJcefTest {
  companion object {
    @ClassRule
    @JvmField
    val appRule: ApplicationRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val disposableRule: DisposableRule = DisposableRule()

  private lateinit var server: TestPageServer

  @Before
  fun before() {
    TestScaleHelper.assumeStandalone()
    TestScaleHelper.assumeHeadful()
    server = TestPageServer()
  }

  @After
  fun after() {
    if (::server.isInitialized) server.close()
    TestScaleHelper.restoreProperties()
  }

  @Test
  fun `the default policy accepts only the main frame`() {
    val browser = createBrowser()
    val query = JBCefJSQuery.create(browser)
    val calls = recordCalls(query)

    val results = load(browser, "page", listOf(query))

    assertEquals(mapOf("page-main" to "ok", "page-same" to REFUSED, "page-sandboxed" to REFUSED), results.of(query))
    assertEquals(listOf("page-main main=true"), calls.toList())
  }

  @Test
  fun `anyFrame accepts the iframes`() {
    val browser = createBrowser()
    val query = JBCefJSQuery.create(browser, FramePolicy.anyFrame())
    val calls = recordCalls(query)

    val results = load(browser, "page", listOf(query))

    assertEquals(mapOf("page-main" to "ok", "page-same" to "ok", "page-sandboxed" to "ok"), results.of(query))
    assertEquals(setOf("page-main main=true", "page-same main=false", "page-sandboxed main=false"), calls.toSet())
  }

  @Test
  fun `mainFrameOf refuses a main frame of another origin`() {
    val browser = createBrowser()
    val ownOrigin = JBCefJSQuery.create(browser, FramePolicy.mainFrameOf(server.mainOrigin))
    val otherOrigin = JBCefJSQuery.create(browser, FramePolicy.mainFrameOf(server.otherOrigin))
    val ownCalls = recordCalls(ownOrigin)
    val otherCalls = recordCalls(otherOrigin)

    val results = load(browser, "page", listOf(ownOrigin, otherOrigin))

    assertEquals(mapOf("page-main" to "ok", "page-same" to REFUSED, "page-sandboxed" to REFUSED), results.of(ownOrigin))
    assertEquals(mapOf("page-main" to REFUSED, "page-same" to REFUSED, "page-sandboxed" to REFUSED), results.of(otherOrigin))
    assertEquals(listOf("page-main main=true"), ownCalls.toList())
    assertEquals(emptyList<String>(), otherCalls.toList())
  }

  @Test
  fun `a second browser of the client is refused`() {
    val client = JBCefApp.getInstance().createClient()
    Disposer.register(disposableRule.disposable, client)
    val own = createBrowser(client)
    val other = createBrowser(client)
    val query = JBCefJSQuery.create(own, FramePolicy.anyFrame())
    val calls = recordCalls(query)

    val otherResults = load(other, "other", listOf(query))
    val ownResults = load(own, "own", listOf(query))

    assertEquals(mapOf("other-main" to REFUSED, "other-same" to REFUSED, "other-sandboxed" to REFUSED), otherResults.of(query))
    assertEquals(mapOf("own-main" to "ok", "own-same" to "ok", "own-sandboxed" to "ok"), ownResults.of(query))
    assertEquals(setOf("own-main main=true", "own-same main=false", "own-sandboxed main=false"), calls.toSet())
  }

  @Test
  fun `a same-origin iframe that calls the function of the main frame is the main frame for CEF`() {
    val browser = createBrowser()
    val query = JBCefJSQuery.create(browser)
    val calls = recordCalls(query)
    val fn = query.funcName

    // Each frame calls the function of its parent when it can reach it. The sandboxed iframe of another origin cannot.
    val script = """
      let owner = window;
      try {
        if (window.parent !== window && typeof window.parent['$fn'] === 'function') owner = window.parent;
      }
      catch (e) {
      }
      owner['$fn']({request: NAME,
                    onSuccess: function(r) { report(NAME, '$fn', 'ok'); },
                    onFailure: function(code, message) { report(NAME, '$fn', 'fail ' + code); }});
    """.trimIndent()
    val results = Results(server.loadAll(browser, "parent", setOf("parent-main", "parent-same", "parent-sandboxed"), listOf(fn), script))

    assertEquals(mapOf("parent-main" to "ok", "parent-same" to "ok", "parent-sandboxed" to REFUSED), results.of(query))
    assertEquals(setOf("parent-main main=true", "parent-same main=true"), calls.toSet())
  }

  @Test
  fun `a guard of a JBCefBrowserBase keeps matching its browser after a garbage collection`() {
    val browser = createBrowser()
    val requests = ConcurrentLinkedQueue<String>()
    val guarded = FramePolicy.mainFrame().guard(browser, object : CefMessageRouterHandlerAdapter() {
      override fun onQuery(browser: CefBrowser, frame: CefFrame, queryId: Long, request: String, persistent: Boolean, callback: CefQueryCallback?): Boolean {
        requests.add(request)
        return true
      }
    })
    GCWatcher.tracking(Any()).ensureCollectedWithinTimeout(30_000)
    assertTrue(guarded.onQuery(browser.cefBrowser, fakeFrame(isMain = true, "https://a.test/"), 1, "call", false, null))
    assertEquals(listOf("call"), requests.toList())
  }

  @Test
  fun `the registry key lets every frame call every query`() {
    Registry.get(JBCefJSQuery.ANY_FRAME_REGISTRY_KEY).setValue(true, disposableRule.disposable)
    val browser = createBrowser()
    val query = JBCefJSQuery.create(browser)
    val calls = recordCalls(query)

    val results = load(browser, "page", listOf(query))

    assertEquals(mapOf("page-main" to "ok", "page-same" to "ok", "page-sandboxed" to "ok"), results.of(query))
    assertEquals(setOf("page-main main=true", "page-same main=false", "page-sandboxed main=false"), calls.toSet())
  }

  @Test
  fun `the clipboard bridge refuses the copy and the paste requests of an iframe that the IDE did not start`() {
    val browser = createBrowser { it.setUseClipboardBridge(true) }
    // The bridge takes a copy of the main frame after a user input, which reaches only a shown browser.
    showInWindow(browser)
    val copies = recordIdeCopies()
    val fn = browser.clipboardBridge!!.queryFuncName
    val iframeText = "iframe-copy-" + System.nanoTime()
    val mainText = "main-copy-" + System.nanoTime()

    // Only the iframes send the requests of the bridge.
    val results = server.loadAll(browser, "clip", setOf("clip-same", "clip-sandboxed"), listOf("copy", "paste"),
                                 bridgeRequestsScript(fn, iframeText), callInMainFrame = false)
    assertEquals(mapOf(("clip-same" to "copy") to REFUSED, ("clip-sandboxed" to "copy") to REFUSED,
                       ("clip-same" to "paste") to REFUSED, ("clip-sandboxed" to "paste") to REFUSED), results)
    flushEdt()
    assertEquals(emptyList<String>(), copies.toList())

    // The same copy request of the main frame after a click reaches the bridge. This shows that the iframe request was a valid one.
    SwingUtilities.invokeAndWait {
      val component = browser.cefBrowser.uiComponent
      component.dispatchEvent(MouseEvent(component, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK,
                                         10, 10, 1, false, MouseEvent.BUTTON1))
    }
    browser.cefBrowser.executeJavaScript(bridgeRequestsScript(fn, mainText).replace("%NAME%", "clip-main"), browser.cefBrowser.url, 0)
    waitFor("the main frame copy in the IDE clipboard") {
      flushEdt()
      copies.contains(mainText)
    }
    assertEquals(listOf(mainText), copies.toList())
  }

  @Test
  fun `the clipboard bridge takes the copy of an iframe that the IDE starts`() {
    val browser = createBrowser { it.setUseClipboardBridge(true) }
    val copies = recordIdeCopies()
    val bridge = browser.clipboardBridge!!

    // The sandboxed iframe of another origin selects its text, and the user focuses it and presses the copy shortcut,
    // which copies in the focused frame.
    val frameId = selectInFocusedIframe(browser, "ide", "")
    SwingUtilities.invokeAndWait { bridge.copy(browser.cefBrowser.getFrameByIdentifier(frameId)) }
    waitFor("the iframe copy in the IDE clipboard") {
      flushEdt()
      copies.contains("ide-sandboxed-selection")
    }
  }

  @Test
  fun `the copy event of each IDE copy in an iframe of another origin finds its token`() {
    val browser = createBrowser { it.setUseClipboardBridge(true) }
    val copies = recordIdeCopies()
    val bridge = browser.clipboardBridge!!
    // The page selects a new text for each copy and reports whether the token of the IDE is there at the copy event.
    val probe = "let current = 0; window.selectText = function(i) { current = i; const text = document.getElementById('text'); " +
                "text.textContent = 'repeated-copy-' + i; document.getSelection().selectAllChildren(text); report(NAME, 'select-' + i, 'ok'); }; " +
                "document.addEventListener('copy', function() { const b = window.__jbCefClipboardBridge; " +
                "report(NAME, 'event-' + current, b && b.copyToken ? 'token' : 'no token'); });"
    val frameId = selectInFocusedIframe(browser, "repeat", probe)

    // The script of the token and the copy command reach the renderer by different channels, so a single copy can pass
    // by chance: the test repeats it.
    for (i in 1..30) {
      val frame = browser.cefBrowser.getFrameByIdentifier(frameId)
      frame.executeJavaScript("selectText($i);", frame.url, 0)
      server.awaitResults(setOf("repeat-sandboxed" to "select-$i"))
      SwingUtilities.invokeAndWait { bridge.copy(browser.cefBrowser.getFrameByIdentifier(frameId)) }
      assertEquals("copy $i", "token", server.awaitResults(setOf("repeat-sandboxed" to "event-$i")).values.single())
      waitFor("copy $i in the IDE clipboard") {
        flushEdt()
        copies.contains("repeated-copy-$i")
      }
    }
  }

  @Test
  fun `a copy of the IDE runs once for one answer of its frame, and another frame cannot answer it`() {
    val browser = createBrowser { it.setUseClipboardBridge(true) }
    val copies = recordIdeCopies()
    val bridge = browser.clipboardBridge!!
    val fn = bridge.queryFuncName
    // The frame reports the selection and the token at each copy event, and at the first one it answers the copy again.
    val probe = "let events = 0; document.addEventListener('copy', function() { events++; " +
                "const token = window.__jbCefClipboardBridge.copyToken; " +
                "report(NAME, 'event-' + events, document.getSelection().toString() + '|' + token); " +
                "if (events === 1) window['$fn']({request: '$COPY_READY' + token, " +
                "onSuccess: function(r) { report(NAME, 'again', 'ok'); }, onFailure: function(code, message) { report(NAME, 'again', 'fail ' + code); }}); });"
    val frameId = selectInFocusedIframe(browser, "answer", probe)

    SwingUtilities.invokeAndWait { bridge.copy(browser.cefBrowser.getFrameByIdentifier(frameId)) }
    val token = server.awaitResults(setOf("answer-sandboxed" to "event-1")).values.single().substringAfter('|')
    assertEquals(REFUSED, server.awaitResults(setOf("answer-sandboxed" to "again")).values.single())
    browser.cefBrowser.executeJavaScript("window['$fn']({request: '$COPY_READY$token', onSuccess: function(r) { report('answer-main', 'again', 'ok'); }, " +
                                         "onFailure: function(code, message) { report('answer-main', 'again', 'fail ' + code); }});",
                                         browser.cefBrowser.url, 0)
    assertEquals(REFUSED, server.awaitResults(setOf("answer-main" to "again")).values.single())
    waitFor("the copy in the IDE clipboard") {
      flushEdt()
      copies.contains("answer-sandboxed-selection")
    }

    // The next copy of the IDE is the second copy event of the frame, so neither answer ran a command.
    val frame = browser.cefBrowser.getFrameByIdentifier(frameId)
    frame.executeJavaScript("(function() { const text = document.getElementById('text'); text.textContent = 'second-copy'; " +
                            "document.getSelection().selectAllChildren(text); report(NAME, 'second', 'ok'); })();", frame.url, 0)
    server.awaitResults(setOf("answer-sandboxed" to "second"))
    SwingUtilities.invokeAndWait { bridge.copy(browser.cefBrowser.getFrameByIdentifier(frameId)) }
    assertEquals("second-copy", server.awaitResults(setOf("answer-sandboxed" to "event-2")).values.single().substringBefore('|'))
  }

  @Test
  fun `a refused answer of a frame leaves no token for the next copy of the page`() {
    val browser = createBrowser { it.setUseClipboardBridge(true) }
    val frameId = selectInFocusedIframe(browser, "stale", "")
    // The frame takes the token of a copy that the IDE did not start, so the bridge refuses its answer.
    val frame = browser.cefBrowser.getFrameByIdentifier(frameId)
    frame.executeJavaScript("window.__jbCefClipboardBridge.takeCopyToken('stale-token'); " +
                            "(function wait() { if (window.__jbCefClipboardBridge.copyToken == null) report(NAME, 'cleared', 'ok'); " +
                            "else setTimeout(wait, 20); })();", frame.url, 0)
    server.awaitResults(setOf("stale-sandboxed" to "cleared"))
  }

  @Test
  fun `the Copy of the native context menu of an iframe goes through the clipboard bridge`() {
    val browser = createBrowser { it.setUseClipboardBridge(true) }
    val copies = recordIdeCopies()
    val bridge = browser.clipboardBridge!!
    val frameId = selectInFocusedIframe(browser, "menu", "")

    val callback = RecordingMenuCallback()
    SwingUtilities.invokeAndWait { CefContextMenuCommands.run(browser.cefBrowser, bridge, frameId, CefMenuModel.MenuId.MENU_ID_COPY, callback) }
    waitFor("the iframe copy in the IDE clipboard") {
      flushEdt()
      copies.contains("menu-sandboxed-selection")
    }
    assertEquals(listOf("cancel"), callback.calls)

    // Without the bridge, Chromium runs the command.
    val nativeCallback = RecordingMenuCallback()
    CefContextMenuCommands.run(browser.cefBrowser, null, frameId, CefMenuModel.MenuId.MENU_ID_COPY, nativeCallback)
    assertEquals(listOf("continue ${CefMenuModel.MenuId.MENU_ID_COPY}"), nativeCallback.calls)
  }

  @Test
  fun `an empty copy that the IDE starts in an iframe uses up its token`() {
    val browser = createBrowser { it.setUseClipboardBridge(true) }
    val copies = recordIdeCopies()
    val bridge = browser.clipboardBridge!!
    // The page copies an empty text, and then reports whether the token of the copy is still there.
    val emptyCopy = "document.addEventListener('copy', function(e) { e.clipboardData.setData('text/plain', ''); e.preventDefault(); " +
                    "setTimeout(function() { report(NAME, 'token', String(window.__jbCefClipboardBridge.copyToken)); }, 100); });"
    val frameId = selectInFocusedIframe(browser, "empty", emptyCopy)

    SwingUtilities.invokeAndWait { bridge.copy(browser.cefBrowser.getFrameByIdentifier(frameId)) }
    assertEquals("null", server.awaitResults(setOf("empty-sandboxed" to "token")).values.single())
    flushEdt()
    assertEquals(emptyList<String>(), copies.toList())
  }

  // Loads the page of the tag, selects a text in each iframe, focuses the sandboxed iframe and returns its identifier.
  private fun selectInFocusedIframe(browser: JBCefBrowser, tag: String, script: String): String {
    val select = "const text = document.getElementById('text'); text.textContent = NAME + '-selection'; " +
                 "document.getSelection().selectAllChildren(text); $script report(NAME, 'select', 'ok');"
    server.loadAll(browser, tag, setOf("$tag-same", "$tag-sandboxed"), listOf("select"), select, callInMainFrame = false)
    SwingUtilities.invokeAndWait { browser.cefBrowser.setFocus(true) }
    browser.cefBrowser.executeJavaScript("document.getElementsByName('sandboxed')[0].focus();", browser.cefBrowser.url, 0)
    waitFor("the focus in the iframe") { browser.cefBrowser.focusedFrame?.url?.contains("$tag-sandboxed") == true }
    return browser.cefBrowser.focusedFrame!!.identifier
  }

  private class RecordingMenuCallback : CefRunContextMenuCallback {
    val calls: MutableList<String> = ArrayList()

    override fun Continue(commandId: Int, eventFlags: Int) {
      calls.add("continue $commandId")
    }

    override fun cancel() {
      calls.add("cancel")
    }
  }

  // The text of each IDE clipboard write. Chromium writes the system clipboard itself on some platforms, so the test
  // observes the writes of the IDE, not the system clipboard.
  private fun recordIdeCopies(): ConcurrentLinkedQueue<String> {
    val copies = ConcurrentLinkedQueue<String>()
    CopyPasteManager.getInstance().addContentChangedListener({ _, newTransferable ->
      if (newTransferable != null && newTransferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        copies.add(newTransferable.getTransferData(DataFlavor.stringFlavor) as String)
      }
    }, disposableRule.disposable)
    return copies
  }

  private fun createBrowser(client: JBCefClient? = null, configure: (JBCefBrowserBuilder) -> Unit = {}): JBCefBrowser {
    val builder = JBCefBrowser.createBuilder().setOffScreenRendering(true)
    if (client != null) builder.setClient(client)
    configure(builder)
    val browser = builder.build()
    Disposer.register(disposableRule.disposable, browser)
    return browser
  }

  private fun showInWindow(browser: JBCefBrowser) {
    SwingUtilities.invokeAndWait {
      val window = JFrame(JBCefJSQueryFramesJcefTest::class.java.simpleName)
      window.setSize(640, 480)
      window.add(browser.component, BorderLayout.CENTER)
      window.isVisible = true
      Disposer.register(disposableRule.disposable) { window.dispose() }
    }
  }

  private fun recordCalls(query: JBCefJSQuery): ConcurrentLinkedQueue<String> {
    val calls = ConcurrentLinkedQueue<String>()
    query.addHandler { _, frame, request ->
      calls.add("$request main=${frame.isMain}")
      null
    }
    return calls
  }

  // Creates the native browser, loads the page and waits for the result of each query in each frame.
  private fun load(browser: JBCefBrowser, tag: String, queries: List<JBCefJSQuery>): Results {
    val frames = setOf("$tag-main", "$tag-same", "$tag-sandboxed")
    val script = queries.joinToString("\n") { query ->
      query.inject("NAME", "function(r) { report(NAME, '${query.funcName}', 'ok'); }",
                   "function(code, message) { report(NAME, '${query.funcName}', 'fail ' + code); }")
    }
    return Results(server.loadAll(browser, tag, frames, queries.map { it.funcName }, script))
  }

  private class Results(private val map: Map<Pair<String, String>, String>) {
    fun of(query: JBCefJSQuery): Map<String, String> =
      map.filterKeys { it.second == query.funcName }.mapKeys { it.key.first }
  }

  // Sends a copy request without a token and a paste request with a wrong token to the query of the bridge.
  @Language("JavaScript")
  private fun bridgeRequestsScript(fn: String, text: String): String = """
    window['$fn']({request: 'copy::' + encodeURIComponent('$text') + '\n',
                  onSuccess: function(r) { report('%NAME%', 'copy', 'ok'); },
                  onFailure: function(code, message) { report('%NAME%', 'copy', 'fail ' + code); }});
    window['$fn']({request: 'paste:a-wrong-token',
                  onSuccess: function(r) { report('%NAME%', 'paste', 'ok'); },
                  onFailure: function(code, message) { report('%NAME%', 'paste', 'fail ' + code); }});
  """.trimIndent()

  private fun flushEdt() {
    SwingUtilities.invokeAndWait { UIUtil.dispatchAllInvocationEvents() }
  }
}

internal fun waitFor(description: String, condition: () -> Boolean) {
  val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
  while (!condition()) {
    if (System.nanoTime() > deadline) fail("Timeout: $description")
    Thread.sleep(50)
  }
}

/**
 * Serves the test page on [mainOrigin] and its sandboxed iframe on [otherOrigin], and collects the results that the frames send.
 */
internal class TestPageServer : AutoCloseable {
  private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
  private val pages = ConcurrentHashMap<String, String>()
  private val results = ConcurrentHashMap<Pair<String, String>, String>()

  val mainOrigin: String get() = "http://127.0.0.1:${server.address.port}"
  val otherOrigin: String get() = "http://localhost:${server.address.port}"

  init {
    server.createContext("/") { exchange ->
      exchange.use {
        val path = exchange.requestURI.path
        if (path == "/report") {
          val parts = URLDecoder.decode(exchange.requestURI.rawQuery, StandardCharsets.UTF_8).split(' ', limit = 3)
          results[parts[0] to parts[1]] = parts[2]
          exchange.sendResponseHeaders(204, -1)
          return@use
        }
        val page = pages[path]
        if (page == null) {
          exchange.sendResponseHeaders(404, -1)
          return@use
        }
        val bytes = page.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-store")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.write(bytes)
      }
    }
    server.start()
  }

  /**
   * Loads the page of [tag] into the browser. Each frame runs [callScript] with `NAME` as its frame name.
   * Without [callInMainFrame], only the iframes run it.
   * Returns the result of each pair of a frame and a query function when all the [frames] report for all the [functions].
   */
  fun loadAll(
    browser: JBCefBrowser,
    tag: String,
    frames: Set<String>,
    functions: List<String>,
    callScript: String,
    callInMainFrame: Boolean = true,
  ): Map<Pair<String, String>, String> {
    pages["/$tag"] = page(tag, if (callInMainFrame) callScript else "", isMain = true)
    pages["/$tag-frame"] = page(tag, callScript, isMain = false)
    SwingUtilities.invokeAndWait {
      browser.createImmediately()
      browser.loadURL("$mainOrigin/$tag")
    }
    return awaitResults(frames.flatMap { frame -> functions.map { frame to it } }.toSet())
  }

  /**
   * Serves [html] at [path].
   */
  fun serve(path: String, html: String) {
    pages[path] = html
  }

  /**
   * Serves the subframe page of [tag] at `/<tag>-frame`. The frame runs [callScript] with `NAME` as its frame name.
   */
  fun serveSubframe(tag: String, callScript: String) {
    pages["/$tag-frame"] = page(tag, callScript, isMain = false)
  }

  /**
   * Waits for the results of all the [expected] pairs of a frame and a function, and returns them.
   */
  fun awaitResults(expected: Set<Pair<String, String>>): Map<Pair<String, String>, String> {
    waitFor("the results of $expected, got ${results.keys}") { results.keys.containsAll(expected) }
    return results.filterKeys { it in expected }
  }

  @Language("HTML")
  private fun page(tag: String, callScript: String, isMain: Boolean): String {
    val frames = if (!isMain) "" else """
      <iframe name="same" src="/$tag-frame?name=$tag-same"></iframe>
      <iframe name="sandboxed" sandbox="allow-scripts" src="$otherOrigin/$tag-frame?name=$tag-sandboxed"></iframe>
    """.trimIndent()
    val name = if (isMain) "'$tag-main'" else "new URLSearchParams(location.search).get('name')"
    return """
      <!DOCTYPE html>
      <html>
      <body>
      <p id="text"></p>
      <script>
        function report(frame, fn, result) {
          fetch('/report?' + encodeURIComponent(frame + ' ' + fn + ' ' + result), {mode: 'no-cors'});
        }
        const NAME = $name;
        ${callScript.replace("'%NAME%'", "NAME")}
      </script>
      $frames
      </body>
      </html>
    """.trimIndent()
  }

  override fun close() {
    server.stop(0)
  }
}
