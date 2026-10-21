// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.ide.GeneralLocalSettings
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.junit5.TestApplication
import org.jdom.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@TestApplication
class WebBrowserManagerStateTest {
  private val chromeId = UUID.fromString("98CA6316-2F89-46D9-A9E5-FA9E2B0625B3")
  private val firefoxId = WebBrowserManager.PREDEFINED_FIREFOX_ID
  private val safariId = UUID.fromString("E5120D43-2C3F-47EF-9F26-65E539E05186")
  private val operaId = UUID.fromString("53E2F627-B1A7-4DFA-BFA7-5B83CC034776")
  private val edgeId = UUID.fromString("37cae5b9-e8b2-4949-9172-aafa37fbc09c")

  private var useDefaultBrowser = true
  private var browserPath = ""

  @BeforeEach
  fun saveGeneralLocalSettings() {
    val settings = GeneralLocalSettings.getInstance()
    useDefaultBrowser = settings.useDefaultBrowser
    browserPath = settings.browserPath
  }

  @AfterEach
  fun restoreGeneralLocalSettings() {
    val settings = GeneralLocalSettings.getInstance()
    settings.useDefaultBrowser = useDefaultBrowser
    settings.browserPath = browserPath
  }

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

  @Test
  fun `system default browser is not in the list`() {
    val manager = WebBrowserManager()

    assertTrue(manager.defaultBrowser === SystemDefaultBrowser)
    assertTrue(manager.browsers.none { it === SystemDefaultBrowser })
    assertTrue(manager.findBrowserById(SystemDefaultBrowser.id.toString()) === SystemDefaultBrowser)
  }

  @Test
  fun `picked default browser survives reload`() {
    val manager = WebBrowserManager()
    manager.setDefaultBrowserId(firefoxId)

    assertEquals("""<state defaultBrowser="$firefoxId" />""", write(manager))
    assertEquals(firefoxId, reload(manager).defaultBrowser.id)
    assertFalse(GeneralLocalSettings.getInstance().useDefaultBrowser)
  }

  @Test
  fun `picking the system default browser writes nothing`() {
    val manager = WebBrowserManager()
    manager.setDefaultBrowserId(firefoxId)
    manager.setDefaultBrowserId(SystemDefaultBrowser.id)

    assertEquals("<state />", write(manager))
    assertTrue(GeneralLocalSettings.getInstance().useDefaultBrowser)
  }

  @Test
  fun `inactive or unknown default browser gives the system default browser`() {
    val inactive = load("""
      <state defaultBrowser="$firefoxId">
        <inactive>
          <browser id="$firefoxId" />
        </inactive>
      </state>
    """)
    assertTrue(inactive.defaultBrowser === SystemDefaultBrowser)

    val unknown = load("""<state defaultBrowser="${UUID.randomUUID()}" />""")
    assertTrue(unknown.defaultBrowser === SystemDefaultBrowser)
  }

  @Test
  fun `first listed policy becomes the first active browser`() {
    val manager = load("""
      <state default="first">
        <active>
          <browser id="$firefoxId" />
          <browser id="$chromeId" />
        </active>
      </state>
    """)

    assertEquals(firefoxId, manager.defaultBrowser.id)
    assertEquals("""<state defaultBrowser="$firefoxId">""", write(manager).lineSequence().first())
  }

  @Test
  fun `first listed policy without an active browser becomes the system default browser`() {
    GeneralLocalSettings.getInstance().useDefaultBrowser = false
    val manager = load("""
      <state default="first">
        <inactive>
          ${listOf(chromeId, firefoxId, safariId, operaId, edgeId).joinToString("") { "<browser id=\"$it\" />" }}
        </inactive>
      </state>
    """)

    assertTrue(manager.defaultBrowser === SystemDefaultBrowser)
    assertTrue(GeneralLocalSettings.getInstance().useDefaultBrowser)
  }

  @Test
  fun `custom command becomes a new browser of the list`() {
    useCustomCommand("/opt/my-browser/run")
    val manager = load("""<state default="alternative" />""")

    val browser = manager.defaultBrowser
    assertEquals("run", browser.name)
    assertEquals("/opt/my-browser/run", browser.path)
    assertNull(browser.family)
    assertTrue(manager.isActive(browser))
    assertTrue(browser in manager.browsers)
  }

  @Test
  fun `custom command migrates once`() {
    useCustomCommand("/opt/my-browser/run")
    val manager = load("""<state default="alternative" />""")

    val reloaded = reload(manager)
    assertEquals(manager.browsers.size, reloaded.browsers.size)
    assertEquals(manager.defaultBrowser.id, reloaded.defaultBrowser.id)
  }

  @Test
  fun `custom command of a known browser gets its family`() {
    useCustomCommand("/opt/firefox-dev/firefox")
    val manager = load("""
      <state default="alternative">
        <inactive>
          <browser id="$firefoxId" />
        </inactive>
      </state>
    """)

    val browser = manager.defaultBrowser
    assertEquals("/opt/firefox-dev/firefox", browser.path)
    assertEquals(BrowserFamily.FIREFOX, browser.family)
  }

  @Test
  fun `custom command reuses an active browser with the same path`() {
    useCustomCommand("/usr/bin/my-chrome")
    val manager = load("""
      <state default="alternative">
        <browsers>
          <browser id="$chromeId" path="/usr/bin/my-chrome" />
        </browsers>
      </state>
    """)

    assertEquals(chromeId, manager.defaultBrowser.id)
    assertEquals(WebBrowserManager().browsers.size, manager.browsers.size)
  }

  @Test
  fun `custom command reuses the browser that it names by id`() {
    useCustomCommand(firefoxId.toString())
    val manager = load("""<state default="alternative" />""")

    assertEquals(firefoxId, manager.defaultBrowser.id)
  }

  @Test
  fun `Internet Explorer custom command becomes the system default browser`() {
    useCustomCommand("C:\\Program Files\\Internet Explorer\\IExplore.exe")
    val manager = load("""<state default="alternative" />""")

    assertTrue(manager.defaultBrowser === SystemDefaultBrowser)
    assertTrue(GeneralLocalSettings.getInstance().useDefaultBrowser)
    assertEquals(WebBrowserManager().browsers.size, manager.browsers.size)
  }

  @Test
  fun `custom path policy with the system default flag becomes the system default browser`() {
    GeneralLocalSettings.getInstance().browserPath = "/opt/my-browser/run"
    GeneralLocalSettings.getInstance().useDefaultBrowser = true
    val manager = load("""<state default="alternative" />""")

    assertTrue(manager.defaultBrowser === SystemDefaultBrowser)
    assertEquals(WebBrowserManager().browsers.size, manager.browsers.size)
  }

  @Test
  fun `system default policy without the system default flag becomes the custom command`() {
    useCustomCommand("/opt/my-browser/run")
    val manager = load("""<state />""")

    assertEquals("/opt/my-browser/run", manager.defaultBrowser.path)
  }

  @Test
  fun `missing state without the system default flag becomes the custom command`() {
    useCustomCommand("/opt/my-browser/run")
    val manager = WebBrowserManager()
    manager.noStateLoaded()

    assertEquals("/opt/my-browser/run", manager.defaultBrowser.path)
  }

  @Suppress("DEPRECATION")
  @Test
  fun `deprecated policy getter reports the system default or a browser of the list`() {
    val manager = WebBrowserManager()
    assertEquals(DefaultBrowserPolicy.SYSTEM, manager.defaultBrowserPolicy)

    manager.setDefaultBrowserId(firefoxId)
    assertEquals(DefaultBrowserPolicy.FIRST, manager.defaultBrowserPolicy)
  }

  private fun useCustomCommand(path: String) {
    GeneralLocalSettings.getInstance().browserPath = path
    GeneralLocalSettings.getInstance().useDefaultBrowser = false
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
