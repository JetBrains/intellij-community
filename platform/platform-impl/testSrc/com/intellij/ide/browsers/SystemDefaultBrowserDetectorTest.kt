// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.ide.browsers.chrome.ChromeSettings
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

@TestApplication
class SystemDefaultBrowserDetectorTest {
  @Test
  fun `Linux desktop entry names the browser`() {
    assertEquals(KnownBrowser.CHROME, knownBrowserOfDesktopEntry("google-chrome.desktop\n"))
    assertEquals(KnownBrowser.CHROME_BETA, knownBrowserOfDesktopEntry("google-chrome-beta.desktop"))
    assertEquals(KnownBrowser.CHROME, knownBrowserOfDesktopEntry("com.google.Chrome.desktop"))
    assertEquals(KnownBrowser.FIREFOX, knownBrowserOfDesktopEntry("org.mozilla.firefox.desktop"))
    assertEquals(KnownBrowser.FIREFOX, knownBrowserOfDesktopEntry("firefox_firefox.desktop"))
    assertEquals(KnownBrowser.EDGE, knownBrowserOfDesktopEntry("microsoft-edge.desktop"))
    assertEquals(KnownBrowser.CHROMIUM, knownBrowserOfDesktopEntry("chromium-browser.desktop"))
    assertNull(knownBrowserOfDesktopEntry("org.gnome.Epiphany.desktop"))
  }

  @Test
  fun `macOS app bundle names the browser`() {
    assertEquals(KnownBrowser.CHROME, knownBrowserOfMacApp("/Applications/Google Chrome.app"))
    assertEquals(KnownBrowser.CHROME_CANARY, knownBrowserOfMacApp("/Users/dev/Applications/Google Chrome Canary.app/"))
    assertEquals(KnownBrowser.SAFARI, knownBrowserOfMacApp("/System/Volumes/Preboot/Cryptexes/App/System/Applications/Safari.app"))
    assertNull(knownBrowserOfMacApp("/Applications/Arc.app"))
  }

  @Test
  fun `Windows command identifies the browser, and the user directory does not matter`() {
    val chromeCommand = """"C:\Users\devon\AppData\Local\Google\Chrome\Application\chrome.exe" --single-argument %1"""
    assertEquals(KnownBrowser.CHROME, knownBrowserOfWindowsProgId("ChromeHTML", chromeCommand))
    val betaCommand = """"C:\Program Files\Google\Chrome Beta\Application\chrome.exe" --single-argument %1"""
    assertEquals(KnownBrowser.CHROME_BETA, knownBrowserOfWindowsProgId("ChromeBHTML", betaCommand))
    val edgeCommand = """C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe --single-argument %1"""
    assertEquals(KnownBrowser.EDGE, knownBrowserOfWindowsProgId("MSEdgeHTM", edgeCommand))
  }

  @Test
  fun `Windows ProgId names the browser without a known command`() {
    assertEquals(KnownBrowser.FIREFOX, knownBrowserOfWindowsProgId("FirefoxURL-308046B0AF4A39CB", null))
    val operaCommand = """"C:\Users\me\AppData\Local\Programs\Opera GX\launcher.exe" -- "%1""""
    assertEquals(KnownBrowser.OPERA_GX, knownBrowserOfWindowsProgId("OperaGXStable", operaCommand))
    assertNull(knownBrowserOfWindowsProgId("AppXq0fevzme2pys62n3e0fbqa7peapykr8v", null))
  }

  @Test
  fun `browser of the list with the default settings opens a page as the OS default browser`() {
    val chrome = WebBrowserManager().addBrowser(UUID.randomUUID(), BrowserFamily.CHROME, "Chrome", "google-chrome", true, ChromeSettings())
    assertTrue(SystemDefaultBrowserDetector.opensSameAs(chrome, KnownBrowser.CHROME))
    assertFalse(SystemDefaultBrowserDetector.opensSameAs(chrome, KnownBrowser.CHROME_BETA))
    assertFalse(SystemDefaultBrowserDetector.opensSameAs(chrome, KnownBrowser.FIREFOX))
  }

  @Test
  fun `browser of the list with its own settings differs from the OS default browser`() {
    val settings = ChromeSettings().apply { commandLineOptions = "--incognito" }
    val chrome = WebBrowserManager().addBrowser(UUID.randomUUID(), BrowserFamily.CHROME, "Chrome", "google-chrome", true, settings)
    assertFalse(SystemDefaultBrowserDetector.opensSameAs(chrome, KnownBrowser.CHROME))
  }
}
