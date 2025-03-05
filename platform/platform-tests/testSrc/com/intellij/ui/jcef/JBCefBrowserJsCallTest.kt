// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.testFramework.ApplicationRule
import com.intellij.ui.scale.TestScaleHelper
import kotlinx.coroutines.*
import org.intellij.lang.annotations.Language
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestName
import java.util.concurrent.CountDownLatch

/**
 * Tests the [AsyncJBCefBrowserJsCall] class and [executeJavaScriptAsync] method.
 */
abstract class JBCefBrowserJsCallTest {
  companion object {
    @ClassRule
    @JvmStatic
    fun getAppRule() = ApplicationRule()
  }

  @Rule
  @JvmField
  val name = TestName()

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
""".trimIndent())
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

  protected abstract fun doTest(@Language("JavaScript") javaScript: String,
                                expectedResult: String? = null,
                                isExpectedToSucceed: Boolean = expectedResult != null)

  protected fun prepareBrowser(): JBCefBrowser {
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


class AsyncJBCefBrowserJsCallTest : JBCefBrowserJsCallTest() {

  @Test
  fun `execute the same call twice and simultaneously`() {
    val browser = prepareBrowser()
    val jsCall = JBCefBrowserJsCall("""2+2""", browser, 0)
    val latch = CountDownLatch(2)

    var r1: String? = null
    var r2: String? = null

    JBCefTestHelper.invokeAndWaitForLatch(latch, "wait js callback") {
      jsCall().onProcessed { latch.countDown() }.onSuccess { r1 = it }
      jsCall().onProcessed { }.onSuccess {
        r2 = it
        latch.countDown()
      }
    }

    assertEquals("4", r1)
    assertEquals("4", r2)
  }


  override fun doTest(@Language("JavaScript") javaScript: String, expectedResult: String?, isExpectedToSucceed: Boolean) {

    val latch = CountDownLatch(1)
    val browser = prepareBrowser()

    var isSucceeded: Boolean? = null
    var actualResult: String? = null

    JBCefTestHelper.invokeAndWaitForLatch(latch, "wait js callback") {
      browser.executeJavaScriptAsync(javaScript)
        .onError { error ->
          println(error.message)
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
}


class SuspendableJBCefBrowserJsCallTest : JBCefBrowserJsCallTest() {

  override fun doTest(@Language("JavaScript") javaScript: String, expectedResult: String?, isExpectedToSucceed: Boolean) {
    val browser = prepareBrowser()

    runBlocking {
      var actualResult: String? = null
      var isSucceeded: Boolean? = null

      try {
        actualResult = browser.executeJavaScript(javaScript)
        isSucceeded = true
      }
      catch (ex: Exception) {
        isSucceeded = false
      }

      assertEquals(isExpectedToSucceed, isSucceeded)

      if (isExpectedToSucceed) {
        assertEquals(expectedResult, actualResult)
      }
    }
  }
}