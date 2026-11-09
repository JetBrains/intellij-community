// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.ide.GeneralLocalSettings
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.UUID

@TestApplication
internal class WebBrowserSettingsCollectorTest {
  @Test
  fun `reports the system default browser`(@TestDisposable disposable: Disposable) {
    val manager = browserManager(disposable)
    manager.defaultBrowserId = null

    assertDefaultBrowser("system_default")
  }

  @Test
  fun `reports Firefox`(@TestDisposable disposable: Disposable) {
    val manager = browserManager(disposable)
    manager.defaultBrowserId = WebBrowserManager.PREDEFINED_FIREFOX_ID

    assertDefaultBrowser("firefox")
  }

  @Test
  fun `reports Chrome`(@TestDisposable disposable: Disposable) {
    val manager = browserManager(disposable)
    manager.defaultBrowserId = WebBrowserManager.PREDEFINED_CHROME_ID

    assertDefaultBrowser("chrome")
  }

  @Test
  fun `reports other predefined browsers as custom`(@TestDisposable disposable: Disposable) {
    val manager = browserManager(disposable)
    val ids = listOf(
      UUID.fromString("E5120D43-2C3F-47EF-9F26-65E539E05186"),
      UUID.fromString("53E2F627-B1A7-4DFA-BFA7-5B83CC034776"),
      UUID.fromString("37cae5b9-e8b2-4949-9172-aafa37fbc09c"),
    )
    for (id in ids) {
      manager.defaultBrowserId = id
      assertDefaultBrowser("custom")
    }
  }

  @Test
  fun `reports an unknown saved browser as custom`(@TestDisposable disposable: Disposable) {
    val manager = browserManager(disposable)
    manager.defaultBrowserId = UUID.randomUUID()
    assertNotEquals(manager.defaultBrowserId, manager.defaultBrowser.id)

    assertDefaultBrowser("custom")
  }

  @Test
  fun `reports the saved IDE Browser when no provider gives the browser`(@TestDisposable disposable: Disposable) {
    val manager = browserManager(disposable)
    manager.defaultBrowserId = WebBrowserManager.IDE_BROWSER_ID
    assertNotEquals(WebBrowserManager.IDE_BROWSER_ID, manager.defaultBrowser.id)

    assertDefaultBrowser("ide_browser")
  }

  @Test
  fun `each collection reads the current selection`(@TestDisposable disposable: Disposable) {
    val manager = browserManager(disposable)
    manager.defaultBrowserId = WebBrowserManager.IDE_BROWSER_ID
    assertDefaultBrowser("ide_browser")

    manager.defaultBrowserId = null
    assertDefaultBrowser("system_default")
  }

  private fun assertDefaultBrowser(expected: String) {
    val collector = WebBrowserSettingsCollector()
    assertEquals("web.browsers.settings", collector.getGroup().id)
    val metric = FUCollectorTestCase
      .collectApplicationStateCollectorEvents(WebBrowserSettingsCollector::class.java)
      .single()
    assertEquals("default.browser", metric.eventId)
    assertEquals(mapOf("browser" to expected), metric.data.build())
  }

  private fun browserManager(disposable: Disposable): WebBrowserManager {
    val manager = WebBrowserManager.getInstance()
    val oldId = manager.defaultBrowserId
    val settings = GeneralLocalSettings.getInstance()
    val oldUseDefaultBrowser = settings.useDefaultBrowser
    val oldBrowserPath = settings.browserPath
    Disposer.register(disposable) {
      manager.defaultBrowserId = oldId
      settings.useDefaultBrowser = oldUseDefaultBrowser
      settings.browserPath = oldBrowserPath
    }
    return manager
  }
}
