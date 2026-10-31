// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.builtInHelp

import com.intellij.ide.browsers.BrowserFamily
import com.intellij.ide.browsers.BrowserSpecificSettings
import com.intellij.ide.browsers.WebBrowser
import com.jetbrains.builtInHelp.settings.SettingsPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.swing.Icon
import javax.swing.ImageIcon

class BrowserSelectionTest {
  @Test
  fun `legacy names resolve to stable IDs`() {
    val id = UUID.fromString("b591b32c-6adc-4fe0-bd63-b2e1bfa71817")
    val browser = browser(id, "Custom browser")
    assertEquals(id.toString(), SettingsPage.resolveBrowserId("Custom browser", listOf(browser)))
    assertEquals(id.toString(), SettingsPage.resolveBrowserId(id.toString(), listOf(browser(id, "Renamed browser"))))
    assertNull(SettingsPage.resolveBrowserId("Removed browser", listOf(browser)))
    assertNull(SettingsPage.resolveBrowserId("<default>", listOf(browser)))
  }

  private fun browser(id: UUID, name: String): WebBrowser = object : WebBrowser() {
    override fun getId(): UUID = id
    override fun getName(): String = name
    override fun getFamily(): BrowserFamily = BrowserFamily.CHROME
    override fun getIcon(): Icon = ImageIcon()
    override fun getPath(): String? = null
    override fun getBrowserNotFoundMessage(): String = "Browser not found"
    override fun getSpecificSettings(): BrowserSpecificSettings? = null
  }
}
