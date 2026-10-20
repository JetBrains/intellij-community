// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

@TestApplication
class WebBrowserNullFamilyTest {
  @Test
  fun `browser without family survives reload`() {
    val manager = WebBrowserManager()
    val id = UUID.randomUUID()
    manager.addBrowser(id, null, "Brave", "/usr/bin/brave", true, null)

    val entry = manager.state!!.getChildren("browser").single { it.getAttributeValue("id") == id.toString() }
    assertNull(entry.getAttributeValue("family"))

    val reloaded = WebBrowserManager()
    reloaded.loadState(manager.state!!)
    val browser = reloaded.browsers.single { it.id == id }
    assertNull(browser.family)
    assertEquals("Brave", browser.name)
    assertEquals("/usr/bin/brave", browser.path)
  }

  @Test
  fun `lookup by family name skips browser without family`() {
    val manager = WebBrowserManager()
    manager.addBrowser(UUID.randomUUID(), null, "Brave", "/usr/bin/brave", true, null)

    assertNull(manager.findBrowserById("unknown"))
  }

  @Test
  fun `browser without family is not Yandex or Edge`() {
    val manager = WebBrowserManager()
    val browser = manager.addBrowser(UUID.randomUUID(), null, "Yandex Edge", null, true, null)

    assertFalse(WebBrowserManager.isYandexBrowser(browser))
    assertFalse(WebBrowserManager.isEdge(browser))
  }
}
