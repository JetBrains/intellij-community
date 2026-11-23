// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.ide.GeneralLocalSettings
import com.intellij.ide.browsers.chrome.ChromeSettings
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
      WebBrowserManager.PREDEFINED_SAFARI_ID,
      WebBrowserManager.PREDEFINED_OPERA_ID,
      WebBrowserManager.PREDEFINED_EDGE_ID,
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

  @Test
  fun `the default settings report only the default browser`(@TestDisposable disposable: Disposable) {
    val manager = browserManager(disposable)
    manager.defaultBrowserId = null

    assertEquals(listOf("default.browser"), metrics().map { it.eventId })
  }

  @Test
  fun `reports the predefined Chromium-based browsers of the debugger`(@TestDisposable disposable: Disposable) {
    val manager = browserManager(disposable)
    manager.chromiumBasedBrowserId = WebBrowserManager.PREDEFINED_EDGE_ID
    assertEquals(listOf(mapOf("browser" to "edge")), data("debug.browser"))

    manager.chromiumBasedBrowserId = WebBrowserManager.PREDEFINED_OPERA_ID
    assertEquals(listOf(mapOf("browser" to "opera")), data("debug.browser"))
  }

  @Test
  fun `reports a custom debugger browser without its name or path`(@TestDisposable disposable: Disposable) {
    val manager = browserManager(disposable)
    val browser = manager.addBrowser(
      UUID.randomUUID(),
      BrowserFamily.CHROME,
      "Brave",
      "/opt/brave",
      true,
      BrowserFamily.CHROME.createBrowserSpecificSettings(),
    )
    manager.chromiumBasedBrowserId = browser.id

    assertEquals(listOf(mapOf("browser" to "custom")), data("debug.browser"))
  }

  @Test
  fun `a missing debugger browser falls back to Chrome and reports nothing`(@TestDisposable disposable: Disposable) {
    val manager = browserManager(disposable)
    manager.chromiumBasedBrowserId = UUID.randomUUID()

    assertEquals(emptyList<Map<String, Any>>(), data("debug.browser"))
  }

  @Test
  fun `a custom browser at the end changes no order`(@TestDisposable disposable: Disposable) {
    val manager = browserManager(disposable)
    manager.list = WebBrowserManager.getPredefinedBrowsers() + customBrowser()

    assertInstalledBrowsers(changes(customAdded = true))
  }

  @Test
  fun `a custom browser before a default browser changes the order`(@TestDisposable disposable: Disposable) {
    val manager = browserManager(disposable)
    manager.list = listOf(customBrowser()) + WebBrowserManager.getPredefinedBrowsers()

    assertInstalledBrowsers(changes(customAdded = true, orderChanged = true))
  }

  @Test
  fun `swapped default browsers change the order`(@TestDisposable disposable: Disposable) {
    val manager = browserManager(disposable)
    val predefined = WebBrowserManager.getPredefinedBrowsers()
    manager.list = listOf(predefined[1], predefined[0]) + predefined.drop(2)

    assertInstalledBrowsers(changes(orderChanged = true))
  }

  @Test
  fun `a disabled default browser changes no order`(@TestDisposable disposable: Disposable) {
    val manager = browserManager(disposable)
    val predefined = WebBrowserManager.getPredefinedBrowsers()
    predefined.single { it.id == WebBrowserManager.PREDEFINED_CHROME_ID }.isActive = false
    manager.list = predefined

    assertInstalledBrowsers(changes(predefinedDisabled = true))
  }

  @Test
  fun `an enabled predefined browser at the end changes no order`(@TestDisposable disposable: Disposable) {
    val manager = browserManager(disposable)
    val predefined = WebBrowserManager.getPredefinedBrowsers()
    val opera = predefined.single { it.id == WebBrowserManager.PREDEFINED_OPERA_ID }
    opera.isActive = true
    manager.list = predefined.filter { it.isActive && it !== opera } + opera + predefined.filter { !it.isActive }

    assertInstalledBrowsers(changes(predefinedEnabled = true))
  }

  @Test
  fun `changed settings of a predefined browser are edited`(@TestDisposable disposable: Disposable) {
    val manager = browserManager(disposable)
    val predefined = WebBrowserManager.getPredefinedBrowsers()
    val chrome = predefined.single { it.id == WebBrowserManager.PREDEFINED_CHROME_ID }
    (chrome.specificSettings as ChromeSettings).commandLineOptions = "--incognito"
    manager.list = predefined

    assertInstalledBrowsers(changes(predefinedEdited = true))
  }

  private fun customBrowser(): ConfigurableWebBrowser =
    ConfigurableWebBrowser(
      UUID.randomUUID(),
      BrowserFamily.CHROME,
      "Brave",
      "/opt/brave",
      true,
      BrowserFamily.CHROME.createBrowserSpecificSettings(),
    )

  private fun assertInstalledBrowsers(expected: Map<String, Any>) {
    assertEquals(listOf(expected), data("installed.browsers"))
  }

  private fun changes(
    customAdded: Boolean = false,
    predefinedDisabled: Boolean = false,
    predefinedEnabled: Boolean = false,
    predefinedEdited: Boolean = false,
    orderChanged: Boolean = false,
  ): Map<String, Any> = mapOf(
    "custom_added" to customAdded,
    "predefined_disabled" to predefinedDisabled,
    "predefined_enabled" to predefinedEnabled,
    "predefined_edited" to predefinedEdited,
    "order_changed" to orderChanged,
  )

  private fun metrics() =
    FUCollectorTestCase.collectApplicationStateCollectorEvents(WebBrowserSettingsCollector::class.java)

  private fun data(eventId: String): List<Map<String, Any>> =
    metrics().filter { it.eventId == eventId }.map { it.data.build() }

  private fun assertDefaultBrowser(expected: String) {
    val collector = WebBrowserSettingsCollector()
    assertEquals("web.browsers.settings", collector.getGroup().id)
    assertEquals(listOf(mapOf("browser" to expected)), data("default.browser"))
  }

  /** Restores the settings of the manager after the test. */
  private fun browserManager(disposable: Disposable): WebBrowserManager {
    val manager = WebBrowserManager.getInstance()
    val oldState = manager.state
    val oldServerReloadMode = manager.webServerReloadMode
    val oldPreviewReloadMode = manager.webPreviewReloadMode
    val oldId = manager.defaultBrowserId
    val settings = GeneralLocalSettings.getInstance()
    val oldUseDefaultBrowser = settings.useDefaultBrowser
    val oldBrowserPath = settings.browserPath
    Disposer.register(disposable) {
      settings.useDefaultBrowser = oldUseDefaultBrowser
      settings.browserPath = oldBrowserPath
      manager.loadState(oldState)
      // the state has no attribute for a default reload mode, so the load keeps the current mode
      manager.webServerReloadMode = oldServerReloadMode
      manager.webPreviewReloadMode = oldPreviewReloadMode
      manager.defaultBrowserId = oldId
      settings.useDefaultBrowser = oldUseDefaultBrowser
      settings.browserPath = oldBrowserPath
    }
    return manager
  }
}
