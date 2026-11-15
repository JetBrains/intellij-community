// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.jcef.JBCefJSQuery.FramePolicy
import com.intellij.ui.jcef.JBCefJSQuery.OwnBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the log of the calls that the frame policy of a query refuses, with fakes of the browser and the frame.
 */
@TestApplication
class JBCefJSQueryRefusalLogTest {
  @Test
  fun `each query logs its first refusal for each origin, for a few origins, with the plugin of its handler`() {
    val prefix = "https://refusal-${System.nanoTime()}"
    val warnings = ArrayList<String>()
    LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
      override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
        if (message.contains(prefix)) warnings.add(message)
        return false
      }
    }) {
      val own = fakeBrowser(1)
      val first = JBCefJSQuery.createRouterHandler(FramePolicy.mainFrame(), OwnBrowser(own), "cefQuery_log_1", javaClass) { _, _, _ -> null }
      val second = JBCefJSQuery.createRouterHandler(FramePolicy.mainFrame(), OwnBrowser(own), "cefQuery_log_2", javaClass) { _, _, _ -> null }
      // The first query refuses more origins than its log keeps, and each origin twice.
      for (i in 0 until JBCefJSQuery.RefusalLog.MAX_ORIGINS + 4) {
        repeat(2) {
          first.onQuery(own, fakeFrame(isMain = false, "$prefix-$i.test/page"), 1, "request", false, null)
        }
      }
      second.onQuery(own, fakeFrame(isMain = false, "$prefix-0.test/page"), 1, "request", false, null)
    }

    assertEquals(JBCefJSQuery.RefusalLog.MAX_ORIGINS, warnings.count { it.startsWith("JBCefJSQuery cefQuery_log_1 of plugin ") }, warnings.toString())
    assertEquals(1, warnings.count { it.startsWith("JBCefJSQuery cefQuery_log_2 of plugin ") }, warnings.toString())
  }

  @Test
  fun `FramePolicy allows logs the refusals of each class of its callers`() {
    val prefix = "https://callers-${System.nanoTime()}"
    val warnings = recordWarnings(prefix) {
      val own = fakeBrowser(2)
      for (i in 0 until JBCefJSQuery.RefusalLog.MAX_ORIGINS + 2) {
        FirstCaller.call(own, fakeFrame(isMain = false, "$prefix-$i.test/page"))
      }
      SecondCaller.call(own, fakeFrame(isMain = false, "$prefix-0.test/page"))
    }

    assertEquals(JBCefJSQuery.RefusalLog.MAX_ORIGINS,
                 warnings.count { it.startsWith("CefMessageRouter handler ${FirstCaller::class.java.name} of plugin ") }, warnings.toString())
    assertEquals(1, warnings.count { it.startsWith("CefMessageRouter handler ${SecondCaller::class.java.name} of plugin ") }, warnings.toString())
  }

  @Test
  fun `in the internal mode the first refusal of a frame with an origin is an error, and of a frame without one a warning`() {
    val errors = ArrayList<String>()
    val warnings = ArrayList<String>()
    LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
      override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
        if (!message.startsWith("internal-log ")) return true
        warnings.add(message)
        return false
      }

      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
        if (!message.startsWith("internal-log ")) return Action.ALL
        errors.add(message)
        return Action.NONE
      }
    }) {
      val log = JBCefJSQuery.RefusalLog("internal-log", null) { true }
      log.refused(FramePolicy.mainFrame(), "a main frame of another origin", null)
      log.refused(FramePolicy.mainFrame(), "a main frame of another origin", "https://a.test")
      log.refused(FramePolicy.mainFrame(), "a main frame of another origin", "https://b.test")
    }

    assertEquals(1, errors.size, errors.toString())
    assertTrue(errors.single().contains("(origin https://a.test, "), errors.toString())
    assertEquals(2, warnings.size, warnings.toString())
    assertTrue(warnings.first().contains("(origin null, "), warnings.toString())
  }
}

private fun recordWarnings(prefix: String, action: () -> Unit): List<String> {
  val warnings = ArrayList<String>()
  LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
    override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
      if (!message.contains(prefix)) return true
      warnings.add(message)
      return false
    }
  }) { action() }
  return warnings
}

private object FirstCaller {
  fun call(own: CefBrowser, frame: CefFrame): Boolean = FramePolicy.mainFrame().allows(own, own, frame)
}

private object SecondCaller {
  fun call(own: CefBrowser, frame: CefFrame): Boolean = FramePolicy.mainFrame().allows(own, own, frame)
}
