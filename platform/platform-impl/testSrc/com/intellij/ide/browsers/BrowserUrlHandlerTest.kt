// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests that the launcher asks a [BrowserUrlHandler] after its guards and before a browser.
 *
 * Each call names a browser of the list, so a URL no handler takes reaches the [UrlOpener] below, which records it
 * and opens nothing.
 */
@TestApplication
class BrowserUrlHandlerTest {
  private class TakingHandler(private val prefix: String) : BrowserUrlHandler {
    val taken = mutableListOf<String>()

    override fun handle(url: String, project: Project?): Boolean {
      if (!url.startsWith(prefix)) return false
      taken += url
      return true
    }
  }

  private class RecordingOpener : UrlOpener() {
    val reached = mutableListOf<String>()

    override fun openUrl(browser: WebBrowser, url: String, project: Project?): Boolean {
      reached += url
      return true
    }
  }

  /** A launcher whose trust guard refuses every URL. */
  private class RefusingLauncher : BrowserLauncherImpl() {
    override fun canBrowse(project: Project?, uri: String): Boolean = false
  }

  private val handler = TakingHandler(REVIEW_LINK_PREFIX)
  private val opener = RecordingOpener()
  private lateinit var browser: WebBrowser

  @BeforeEach
  fun registerExtensions(@TestDisposable disposable: Disposable) {
    BrowserUrlHandler.EP_NAME.point.registerExtension(handler, disposable)
    UrlOpener.EP_NAME.point.registerExtension(opener, disposable)
    browser = WebBrowserManager.getInstance().browsers.first()
  }

  @Test
  fun `a handler that takes the url keeps it from the browser`() {
    BrowserLauncherImpl().browse(REVIEW_LINK, browser, project = null)

    assertEquals(listOf(REVIEW_LINK), handler.taken)
    assertTrue(opener.reached.isEmpty())
  }

  @Test
  fun `a url no handler takes reaches the browser`() {
    BrowserLauncherImpl().browse("https://www.jetbrains.com", browser, project = null)

    assertTrue(handler.taken.isEmpty())
    assertEquals(listOf("https://www.jetbrains.com"), opener.reached)
  }

  @Test
  fun `a url the guards refuse never reaches a handler`() {
    RefusingLauncher().browse(REVIEW_LINK, browser, project = null)

    assertTrue(handler.taken.isEmpty())
    assertTrue(opener.reached.isEmpty())
  }
}

private const val REVIEW_LINK_PREFIX = "jetbrains://open/"
private const val REVIEW_LINK = "${REVIEW_LINK_PREFIX}code_findings/review?id=rev-1"
