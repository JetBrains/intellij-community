// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.junit5.TestApplication
import org.jdom.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

@TestApplication
class WebBrowserManagerStateTest {
  private val chromeId = UUID.fromString("98CA6316-2F89-46D9-A9E5-FA9E2B0625B3")
  private val firefoxId = WebBrowserManager.PREDEFINED_FIREFOX_ID

  @Test
  fun `default state writes nothing`() {
    assertEquals("<state />", write(WebBrowserManager()))
  }

  @Test
  fun `inactive browser writes only its id`() {
    val manager = WebBrowserManager()
    val firefox = manager.browser(firefoxId)
    manager.setBrowserPath(firefox, firefox.path, false)

    val expected = """
      <state>
        <inactive>
          <browser id="$firefoxId" />
        </inactive>
      </state>
    """.trimIndent()
    assertEquals(expected, write(manager))
    assertEquals(expected, write(reload(manager)))
  }

  @Test
  fun `legacy state migrates to new format`() {
    val defaults = WebBrowserManager()
    val legacy = Element("state")
    for (browser in defaults.browsers) {
      val entry = Element("browser")
        .setAttribute("id", browser.id.toString())
        .setAttribute("name", browser.name)
        .setAttribute("family", browser.family!!.name)
      browser.path?.let { entry.setAttribute("path", it) }
      if (!defaults.isActive(browser) || browser.id == firefoxId) {
        entry.setAttribute("active", "false")
      }
      legacy.addContent(entry)
    }

    val manager = WebBrowserManager()
    manager.loadState(legacy)

    assertEquals("""
      <state>
        <inactive>
          <browser id="$firefoxId" />
        </inactive>
      </state>
    """.trimIndent(), write(manager))
  }

  @Test
  fun `active order survives reload`() {
    val manager = load("""
      <state>
        <active>
          <browser id="$firefoxId" />
          <browser id="$chromeId" />
        </active>
      </state>
    """)

    val activeIds = manager.activeBrowsers.map { it.id }
    assertEquals(listOf(firefoxId, chromeId), activeIds.take(2))
    assertEquals(activeIds, reload(manager).activeBrowsers.map { it.id })
  }

  @Test
  fun `activated browser goes after other active browsers`() {
    val manager = WebBrowserManager()
    val inactive = manager.browsers.first { !manager.isActive(it) }
    manager.setBrowserPath(inactive, inactive.path, true)

    assertEquals(inactive.id, reload(manager).activeBrowsers.last().id)
  }

  @Test
  fun `custom inactive browser survives reload`() {
    val manager = WebBrowserManager()
    val custom = manager.addBrowser(UUID.randomUUID(), BrowserFamily.CHROME, "Brave", "/usr/bin/brave", false, null)
    assertNull(manager.state.getChild("active"))

    val reloaded = reload(manager)
    val reloadedCustom = reloaded.browser(custom.id)
    assertFalse(reloaded.isActive(reloadedCustom))
    assertEquals("Brave", reloadedCustom.name)
    assertEquals("/usr/bin/brave", reloadedCustom.path)
  }

  @Test
  fun `predefined browser keeps changed name and cleared path`() {
    val state = """
      <state>
        <browsers>
          <browser id="$chromeId" name="My Chrome" path="" />
        </browsers>
      </state>
    """.trimIndent()
    val manager = load(state)

    val chrome = manager.browser(chromeId)
    assertEquals("My Chrome", chrome.name)
    assertNull(chrome.path)
    assertTrue(manager.isActive(chrome))
    assertEquals(state, write(manager))
  }

  @Test
  fun `unlisted predefined browser that is active by default becomes active`() {
    val manager = load("""
      <state>
        <active>
          <browser id="$chromeId" />
        </active>
      </state>
    """)

    val activeIds = manager.activeBrowsers.map { it.id }
    assertEquals(chromeId, activeIds.first())
    assertTrue(firefoxId in activeIds)
  }

  private fun WebBrowserManager.browser(id: UUID): WebBrowser = browsers.first { it.id == id }

  private fun write(manager: WebBrowserManager): String = JDOMUtil.write(manager.state)

  private fun load(state: String): WebBrowserManager {
    val manager = WebBrowserManager()
    manager.loadState(JDOMUtil.load(state.trimIndent()))
    return manager
  }

  private fun reload(manager: WebBrowserManager): WebBrowserManager {
    val reloaded = WebBrowserManager()
    reloaded.loadState(manager.state)
    return reloaded
  }
}
