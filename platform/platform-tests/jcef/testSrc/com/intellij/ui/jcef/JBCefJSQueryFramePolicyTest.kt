// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.ui.jcef.JBCefJSQuery.FramePolicy
import com.intellij.ui.jcef.JBCefJSQuery.OwnBrowser
import com.intellij.util.ref.GCWatcher
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandler
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.Proxy

/**
 * Tests the logic of [FramePolicy] and the router handler of a query without JCEF, with fakes of the browser and the frame.
 * [JBCefJSQueryFramesJcefTest] tests the policies in real JCEF.
 */
class JBCefJSQueryFramePolicyTest {
  @Test
  fun `the origin of a URL is the scheme, the host and a port that is not the default one`() {
    assertEquals("https://example.com", FramePolicy.originOf("https://example.com/a/b?c=d#e"))
    assertEquals("https://example.com", FramePolicy.originOf("HTTPS://User:Password@Example.COM:443/path"))
    assertEquals("https://example.com:8443", FramePolicy.originOf("https://example.com:8443"))
    assertEquals("http://localhost:63342", FramePolicy.originOf("http://localhost:63342/project/index.html"))
    assertEquals("http://127.0.0.1", FramePolicy.originOf("http://127.0.0.1:80/"))
    assertEquals("http://[::1]:8080", FramePolicy.originOf("http://[::1]:8080/x"))
    assertEquals("ws://example.com", FramePolicy.originOf("ws://example.com:80/socket"))
    assertEquals("jcef://resources", FramePolicy.originOf("jcef://resources/index.html"))
    assertEquals("https://example.com", FramePolicy.originOf("https://example.com?x=1"))
    assertEquals("https://example.com", FramePolicy.originOf("https://example.com#x"))
    assertEquals("https://example.com", FramePolicy.originOf("https://example.com\\path"))
  }

  @Test
  fun `every file URL has the origin file`() {
    assertEquals("file://", FramePolicy.originOf("file:///jbcefbrowser/123#url=about:blank"))
    assertEquals("file://", FramePolicy.originOf("file://host/share/page.html"))
    assertEquals("file://", FramePolicy.originOf("file:/tmp/page.html"))
  }

  @Test
  fun `a URL without a host has no origin`() {
    assertNull(FramePolicy.originOf(null))
    assertNull(FramePolicy.originOf(""))
    assertNull(FramePolicy.originOf("about:blank"))
    assertNull(FramePolicy.originOf("about:srcdoc"))
    assertNull(FramePolicy.originOf("data:text/html,<script>alert(1)</script>"))
    assertNull(FramePolicy.originOf("javascript:alert(1)"))
    assertNull(FramePolicy.originOf("blob:https://example.com/0b4e8b64"))
    assertNull(FramePolicy.originOf("https:///path"))
    assertNull(FramePolicy.originOf("https://example.com:99999/"))
    assertNull(FramePolicy.originOf("https://example.com:8a/"))
    assertNull(FramePolicy.originOf("//example.com/path"))
    assertNull(FramePolicy.originOf("1https://example.com"))
  }

  @Test
  fun `mainFrame accepts only the main frame of the own browser`() {
    val policy = FramePolicy.mainFrame()
    assertNull(policy.check(true, true, "https://example.com/"))
    assertNull(policy.check(true, true, "about:blank"))
    assertEquals("a subframe", policy.check(true, false, "https://example.com/"))
    assertEquals("another browser of the client", policy.check(false, true, "https://example.com/"))
  }

  @Test
  fun `mainFrameOf accepts only the main frame of the own browser on a listed origin`() {
    val policy = FramePolicy.mainFrameOf("https://example.com", "HTTP://LOCALHOST:63342/", "file://")
    assertNull(policy.check(true, true, "https://example.com/page"))
    assertNull(policy.check(true, true, "https://example.com:443/page"))
    assertNull(policy.check(true, true, "http://localhost:63342/project/index.html"))
    assertNull(policy.check(true, true, "file:///jbcefbrowser/123"))
    assertEquals("a main frame of another origin", policy.check(true, true, "https://example.com.other.test/"))
    assertEquals("a main frame of another origin", policy.check(true, true, "http://example.com/"))
    assertEquals("a main frame of another origin", policy.check(true, true, "http://localhost:63343/"))
    assertEquals("a main frame of another origin", policy.check(true, true, "about:blank"))
    assertEquals("a main frame of another origin", policy.check(true, true, null))
    assertEquals("a subframe", policy.check(true, false, "https://example.com/page"))
    assertEquals("another browser of the client", policy.check(false, true, "https://example.com/page"))
  }

  @Test
  fun `anyFrame accepts every frame of the own browser`() {
    val policy = FramePolicy.anyFrame()
    assertNull(policy.check(true, true, "https://example.com/"))
    assertNull(policy.check(true, false, "https://other.test/"))
    assertNull(policy.check(true, false, "about:srcdoc"))
    assertEquals("another browser of the client", policy.check(false, false, "https://example.com/"))
  }

  @Test
  fun `mainFrameOf converts an internationalized host name to its ASCII form`() {
    val policy = FramePolicy.mainFrameOf("https://bücher.example")
    assertNull(policy.check(true, true, "https://xn--bcher-kva.example/page"))
    assertEquals("FramePolicy.mainFrameOf(https://xn--bcher-kva.example)", policy.toString())
  }

  @Test
  fun `a refused call fails with the code of the frame policy and does not reach the handler`() {
    val own = fakeBrowser(1)
    val requests = ArrayList<String>()
    val handler = JBCefJSQuery.createRouterHandler(FramePolicy.mainFrame(), OwnBrowser(own), "cefQuery_test_refused", javaClass) { _, _, request ->
      requests.add(request)
      JBCefJSQuery.Response("ok")
    }

    assertEquals(listOf("failure -403"), query(handler, own, fakeFrame(isMain = false, "https://a.test/"), "subframe"))
    assertEquals(listOf("failure -403"), query(handler, fakeBrowser(2), fakeFrame(isMain = true, "https://a.test/"), "other browser"))
    assertEquals(listOf("failure -403"), query(handler, own, null, "no frame"))
    assertEquals(listOf("success ok"), query(handler, own, fakeFrame(isMain = true, "https://a.test/"), "main frame"))
    assertEquals(listOf("main frame"), requests)
  }

  @Test
  fun `the own browser matches by object or by identifier`() {
    val own = fakeBrowser(7)
    val ownBrowser = OwnBrowser(own)
    assertTrue(ownBrowser.matches(own))
    assertTrue(ownBrowser.matches(fakeBrowser(7)))
    assertFalse(ownBrowser.matches(fakeBrowser(8)))
    assertFalse(ownBrowser.matches(fakeBrowser(-1)))

    // A browser that is not created yet has no identifier, so only the same object matches it.
    val notCreated = fakeBrowser(-1)
    assertTrue(OwnBrowser(notCreated).matches(notCreated))
    assertFalse(OwnBrowser(notCreated).matches(fakeBrowser(-1)))
  }

  @Test
  fun `the own browser matches nothing when it is gone, also by its identifier`() {
    val (ownBrowser, watcher) = createOwnBrowserOfForgottenBrowser()
    watcher.ensureCollectedWithinTimeout(30_000)
    assertFalse(ownBrowser.matches(fakeBrowser(9)))
  }

  @Test
  fun `a guard of a CefBrowser object that nobody else holds stops matching after a garbage collection`() {
    val (guarded, watcher) = createGuardOfForgottenBrowser()
    watcher.ensureCollectedWithinTimeout(30_000)
    assertEquals(false to emptyList<String>(), queryAndResult(guarded, fakeBrowser(11), fakeFrame(isMain = true, "https://a.test/"), "call"))
  }

  @Test
  fun `the router handler of a query does not keep its browser`() {
    val (handler, watcher) = createHandlerOfForgottenBrowser()
    watcher.ensureCollectedWithinTimeout(30_000)
    assertEquals(listOf("failure -403"), query(handler, fakeBrowser(3), fakeFrame(isMain = true, "https://a.test/"), "request"))
  }

  @Test
  fun `guard passes only the calls that the policy accepts to the handler`() {
    val own = fakeBrowser(4)
    val requests = ArrayList<String>()
    val canceled = ArrayList<Long>()
    val guarded = FramePolicy.mainFrameOf("https://a.test").guard(own, object : CefMessageRouterHandlerAdapter() {
      override fun onQuery(browser: CefBrowser, frame: CefFrame, queryId: Long, request: String, persistent: Boolean, callback: CefQueryCallback): Boolean {
        requests.add(request)
        callback.success("ok")
        return true
      }

      override fun onQueryCanceled(browser: CefBrowser, frame: CefFrame, queryId: Long) {
        canceled.add(queryId)
      }
    })

    // The handler of another browser on the same router can take the call of that browser.
    assertEquals(false to emptyList<String>(), queryAndResult(guarded, fakeBrowser(5), fakeFrame(isMain = true, "https://a.test/"), "popup"))
    assertEquals(listOf("failure -403"), query(guarded, own, fakeFrame(isMain = false, "https://a.test/"), "subframe"))
    assertEquals(listOf("failure -403"), query(guarded, own, fakeFrame(isMain = true, "https://b.test/"), "other origin"))
    assertEquals(listOf("success ok"), query(guarded, own, fakeFrame(isMain = true, "https://a.test/x"), "main frame"))
    assertEquals(listOf("main frame"), requests)
    guarded.onQueryCanceled(own, fakeFrame(isMain = true, "https://a.test/"), 42)
    assertEquals(listOf(42L), canceled)
  }

  @Test
  fun `mainFrameOf accepts only origins`() {
    assertThrows<IllegalArgumentException> { FramePolicy.mainFrameOf() }
    assertThrows<IllegalArgumentException> { FramePolicy.mainFrameOf("https://example.com/path") }
    assertThrows<IllegalArgumentException> { FramePolicy.mainFrameOf("https://example.com?x=1") }
    assertThrows<IllegalArgumentException> { FramePolicy.mainFrameOf("https://user@example.com") }
    assertThrows<IllegalArgumentException> { FramePolicy.mainFrameOf("example.com") }
    assertThrows<IllegalArgumentException> { FramePolicy.mainFrameOf("about:blank") }
    assertThrows<IllegalArgumentException> { FramePolicy.mainFrameOf("file:///tmp/page.html") }
    assertEquals("FramePolicy.mainFrameOf(https://example.com, file://)",
                 FramePolicy.mainFrameOf("https://example.com:443", "file:///").toString())
  }
}

// The own browser matches another object of the browser first, so it keeps the identifier of the browser.
private fun createOwnBrowserOfForgottenBrowser(): Pair<OwnBrowser, GCWatcher> {
  val own = fakeBrowser(9)
  val ownBrowser = OwnBrowser(own)
  assertTrue(ownBrowser.matches(fakeBrowser(9)))
  return ownBrowser to GCWatcher.tracking(own)
}

// The guard matches another object of the browser first, so it keeps the identifier of the browser.
private fun createGuardOfForgottenBrowser(): Pair<CefMessageRouterHandler, GCWatcher> {
  val own = fakeBrowser(11)
  val guarded = FramePolicy.mainFrame().guard(own, object : CefMessageRouterHandlerAdapter() {
    override fun onQuery(browser: CefBrowser, frame: CefFrame, queryId: Long, request: String, persistent: Boolean, callback: CefQueryCallback): Boolean {
      callback.success("ok")
      return true
    }
  })
  assertEquals(true to listOf("success ok"), queryAndResult(guarded, fakeBrowser(11), fakeFrame(isMain = true, "https://a.test/"), "call"))
  return guarded to GCWatcher.tracking(own)
}

private fun createHandlerOfForgottenBrowser(): Pair<CefMessageRouterHandler, GCWatcher> {
  val own = fakeBrowser(3)
  val handler = JBCefJSQuery.createRouterHandler(FramePolicy.mainFrame(), OwnBrowser(own), "cefQuery_test_weak", Any::class.java) { _, _, _ -> null }
  return handler to GCWatcher.tracking(own)
}

private fun query(handler: CefMessageRouterHandler, browser: CefBrowser, frame: CefFrame?, request: String): List<String> {
  val (handled, results) = queryAndResult(handler, browser, frame, request)
  assertTrue(handled)
  return results
}

private fun queryAndResult(handler: CefMessageRouterHandler, browser: CefBrowser, frame: CefFrame?, request: String): Pair<Boolean, List<String>> {
  val results = ArrayList<String>()
  val handled = handler.onQuery(browser, frame, 1, request, false, object : CefQueryCallback {
    override fun success(response: String) {
      results.add("success $response")
    }

    override fun failure(error_code: Int, error_message: String?) {
      results.add("failure $error_code")
    }
  })
  return handled to results
}

internal fun fakeBrowser(identifier: Int): CefBrowser = fake { name -> if (name == "getIdentifier") identifier else null }

internal fun fakeFrame(isMain: Boolean, url: String): CefFrame = fake { name ->
  when (name) {
    "isMain" -> isMain
    "getURL" -> url
    else -> null
  }
}

private inline fun <reified T : Any> fake(crossinline answer: (String) -> Any?): T {
  return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
    when (method.name) {
      "equals" -> proxy === args?.get(0)
      "hashCode" -> System.identityHashCode(proxy)
      "toString" -> "fake ${T::class.java.simpleName}"
      else -> answer(method.name) ?: throw UnsupportedOperationException(method.name)
    }
  } as T
}
