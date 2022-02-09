// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.testFramework.ApplicationRule
import com.intellij.ui.scale.TestScaleHelper
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.util.concurrent.CountDownLatch


/**
 * Tests the [JBCefBrowserJsCall] class and [executeJavaScriptAsync] method.
 */
class JBCefBrowserJsCallTest {

  companion object {
    @ClassRule
    @JvmStatic
    public fun getAppRule() = ApplicationRule()
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

    TestCase.assertEquals("4", r1)
    TestCase.assertEquals("4", r2)
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

    TestCase.assertEquals(isExpectedToSucceed, isSucceeded)

    if (isExpectedToSucceed) {
      TestCase.assertEquals(expectedResult, actualResult)
    }
  }


  private fun prepareBrowser(): JBCefBrowser {
    val browser = JBCefApp.getInstance().createClient().also {
      it.setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, 24)
    }.let { jbCefClient ->
      JBCefBrowser.createBuilder()
        .setClient(jbCefClient)
        .setUrl("about:blank")
        .createBrowser()
    }

    JBCefTestHelper.showAndWaitForLoad(browser, "DISPATCH")

    TestCase.assertNotNull(browser.component)
    TestCase.assertTrue(browser.isCefBrowserCreated)

    return browser
  }
}