// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.testFramework.ApplicationRule
import com.intellij.ui.scale.TestScaleHelper
import org.cef.misc.CefLog
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.util.concurrent.CountDownLatch

/**
 * Tests the [JBCefBrowserJsCall] class and [executeJavaScriptAsync] method.
 */
class JBCefBrowserJsCallTest {
  companion object {
    @ClassRule @JvmStatic fun getAppRule() = ApplicationRule()
  }

  @Before
  fun before() {
    TestScaleHelper.assumeStandalone()
    TestScaleHelper.setSystemProperty("java.awt.headless", "false")
  }

  @After
  fun after() {
    TestScaleHelper.restoreProperties()
  }


  @Test
  fun `execute unresolved expression`() {
    doTest("""foo.bar""")
  }

  @Test
  fun `execute throw expression`() {
    doTest(""" 
      let r = 2 + 2
      throw 'error'
      return r
""".trimIndent()
    )
  }

  @Test
  fun `execute math operation`() {
    doTest("""2+2""", "4")
  }

  // IDEA-290310, IDEA-292709
  @Test
  fun `obtain a stringified JSON with emoji`() {
    doTest(javaScript = """
          let json = JSON.stringify({ "a": "foo", "cookie": "🍪"})
          return json;
        """.trimIndent(), expectedResult = """{"a":"foo","cookie":"🍪"}""")
  }

  // IDEA-290310, IDEA-292709
  @Test
  fun `obtain a string with emoji`() {
    doTest(javaScript = """
          let emoji = `🍪`
          return emoji;
        """.trimIndent(), expectedResult = """🍪""")
  }

  // IDEA-288813
  @Test
  fun `obtain a string decoded from base64`() {
    // NOTE: non-latin symbols in JS.atob should be handled in special way, see:
    // https://stackoverflow.com/questions/3626183/javascript-base64-encoding-utf8-string-fails-in-webkit-safari
    // https://stackoverflow.com/questions/30106476/using-javascripts-atob-to-decode-base64-doesnt-properly-decode-utf-8-strings
    doTest(javaScript = """
          let decoded_string = atob("U29tZSB0ZXh0INC4INC60LDQutC+0Lkt0YLQviDRgtC10LrRgdGC");
          return decodeURIComponent(escape(decoded_string))
        """.trimIndent(), expectedResult = """Some text и какой-то текст""")
  }

  @Test
  fun `execute multiline expression`() {
    val js = """
      function myFunction(p1, p2) {
        return p1 * p2;   // The function returns the product of p1 and p2
      };
      
      return myFunction(2,2)
    """.trimIndent()
    doTest(js, "4")
  }

  @Test
  fun `execute the same call twice and simultaneously`() {
    val browser = prepareBrowser()
    val jsCall = JBCefBrowserJsCall("""2+2""", browser)
    val latch = CountDownLatch(2)

    var r1: String? = null
    var r2: String? = null

    JBCefTestHelper.invokeAndWaitForLatch(latch) {
      jsCall().onProcessed { latch.countDown() }.onSuccess { r1 = it }
      jsCall().onProcessed { latch.countDown() }.onSuccess { r2 = it }
    }

    assertEquals("4", r1)
    assertEquals("4", r2)
  }

  // TODO: remove when IDEA-312158 fixed
  @Test
  fun `IDEA-312158 with logging`() {
    val browser = prepareBrowser()
    CefLog.Info("Start IDEA-312158 test with browser " + browser.cefBrowser.uiComponent)
    val javaScript = """
          console.log("****** exec JS ****** ");
          return 2+2;
        """.trimIndent()
    val jsCall = JBCefBrowserJsCall(javaScript, browser)
    val latch = CountDownLatch(2)

    var r1: String? = null
    var r2: String? = null

    JBCefTestHelper.invokeAndWaitForLatch(latch) {
      jsCall().onProcessed {
        CefLog.Info("onProcessed");
        latch.countDown()
      }.onSuccess {
        CefLog.Info("Success, r1=%s", it)
        r1 = it
      }
      jsCall().onProcessed {
        CefLog.Info("onProcessed");
        latch.countDown()
      }.onSuccess {
        CefLog.Info("Success, r2=%s", it)
        r2 = it
      }
    }

    assertEquals("4", r1)
    assertEquals("4", r2)
  }

  // TODO: remove when IDEA-312158 fixed
  @Test
  fun `IDEA-312158 with fix`() {
    val browser = prepareBrowser()
    CefLog.Info("Start IDEA-312158 test with browser " + browser.cefBrowser.uiComponent)
    val javaScript = """
          console.log("****** exec JS ****** ");
          return 2+2;
        """.trimIndent()
    val jsCall = JBCefBrowserJsCall(javaScript, browser)
    val latch = CountDownLatch(2)

    var r1: String? = null
    var r2: String? = null

    JBCefTestHelper.invokeAndWaitForLatch(latch) {
      jsCall().onProcessed {
        CefLog.Info("onProcessed");
        latch.countDown()
      }.onSuccess {
        CefLog.Info("Success, r1=%s", it)
        r1 = it
      }
      jsCall().onProcessed {
        CefLog.Info("onProcessed");
      }.onSuccess {
        CefLog.Info("Success, r2=%s", it)
        r2 = it
        latch.countDown()
      }
    }

    assertEquals("4", r1)
    assertEquals("4", r2)
  }

  private fun doTest(@Language("JavaScript") javaScript: String,
                     expectedResult: String? = null,
                     isExpectedToSucceed: Boolean = expectedResult != null) {

    val latch = CountDownLatch(1)
    val browser = prepareBrowser()

    var isSucceeded: Boolean? = null
    var actualResult: String? = null

    JBCefTestHelper.invokeAndWaitForLatch(latch) {
      browser.executeJavaScriptAsync(javaScript)
        .onError {
          println(it.message)
          isSucceeded = false
        }.onSuccess {
          isSucceeded = true
          actualResult = it
        }.onProcessed {
          latch.countDown()
        }
    }

    assertEquals(isExpectedToSucceed, isSucceeded)

    if (isExpectedToSucceed) {
      assertEquals(expectedResult, actualResult)
    }
  }

  private fun prepareBrowser(): JBCefBrowser {
    // enable verbose logging in tests for investigating intermittent problems
    // see https://youtrack.jetbrains.com/issue/IDEA-312158
    System.setProperty("ide.browser.jcef.log.level", "verbose");
    System.setProperty("ide.browser.jcef.log.path", " ");
    System.setProperty("jcef.trace.cefbrowser_n.lifespan", "true");
    System.setProperty("ide.browser.jcef.debug.js", "true");

    val browser = JBCefApp.getInstance().createClient().also {
      it.setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, 24)
    }.let { jbCefClient ->
      JBCefBrowser.createBuilder()
        .setClient(jbCefClient)
        .setUrl("about:blank")
        .build()
    }

    JBCefTestHelper.showAndWaitForLoad(browser, "DISPATCH")

    assertNotNull(browser.component)
    assertTrue(browser.isCefBrowserCreated)

    return browser
  }
}
