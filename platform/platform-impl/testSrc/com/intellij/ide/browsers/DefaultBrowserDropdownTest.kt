// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.ide.GeneralLocalSettings
import com.intellij.ide.browsers.BrowserLauncherAppless.Companion.canUseSystemDefaultBrowserPolicy
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import org.jdom.Element
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JComboBox

/**
 * Tests the "Default Browser" dropdown of [BrowserSettingsConfigurable].
 */
@TestApplication
class DefaultBrowserDropdownTest {
  private val chromeId = WebBrowserManager.PREDEFINED_CHROME_ID
  private val firefoxId = WebBrowserManager.PREDEFINED_FIREFOX_ID

  private lateinit var savedState: Element
  private var savedUseDefaultBrowser = true
  private var savedBrowserPath = ""

  @BeforeEach
  fun setUp() {
    savedState = WebBrowserManager.getInstance().state
    savedUseDefaultBrowser = GeneralLocalSettings.getInstance().useDefaultBrowser
    savedBrowserPath = GeneralLocalSettings.getInstance().browserPath
  }

  @AfterEach
  fun tearDown() {
    WebBrowserManager.getInstance().loadState(savedState)
    GeneralLocalSettings.getInstance().useDefaultBrowser = savedUseDefaultBrowser
    GeneralLocalSettings.getInstance().browserPath = savedBrowserPath
  }

  @Test
  fun `dropdown offers the active browsers and selects the stored one`() {
    loadState("""<state defaultBrowser="$firefoxId" />""")

    withPage { _, comboBox ->
      val items = (0 until comboBox.itemCount).map { comboBox.getItemAt(it) }
      assertEquals(canUseSystemDefaultBrowserPolicy(), items.any { it.id == SystemDefaultBrowser.id })
      val listedIds = items.map { it.id }.filter { it != SystemDefaultBrowser.id }
      val manager = WebBrowserManager.getInstance()
      assertEquals(manager.activeBrowsers.filter { it.path != null }.map { it.id }, listedIds)
      assertEquals(firefoxId, (comboBox.selectedItem as BrowserChoice).id)
    }
  }

  @Test
  fun `dropdown selects the system default browser by default`() {
    loadState("""<state />""")

    withPage { page, comboBox ->
      val expected = if (canUseSystemDefaultBrowserPolicy()) SystemDefaultBrowser.id else WebBrowserManager.getInstance().firstActiveBrowser?.id
      assertEquals(expected, (comboBox.selectedItem as BrowserChoice?)?.id)
      assertFalse(page.isModified)
    }
  }

  @Test
  fun `apply stores a listed browser by its id`() {
    loadState("""<state />""")

    withPage { page, comboBox ->
      comboBox.selectedItem = (0 until comboBox.itemCount).map { comboBox.getItemAt(it) }
        .first { it.id == chromeId }
      assertTrue(page.isModified)
      page.apply()
      assertFalse(page.isModified)
    }

    val manager = WebBrowserManager.getInstance()
    assertEquals(chromeId, manager.defaultBrowser.id)
    assertFalse(GeneralLocalSettings.getInstance().useDefaultBrowser)
  }

  private fun loadState(state: String) {
    WebBrowserManager.getInstance().loadState(JDOMUtil.load(state))
  }

  @Suppress("UNCHECKED_CAST")
  private fun withPage(action: (BrowserSettingsConfigurable, JComboBox<BrowserChoice>) -> Unit) {
    runInEdtAndWait {
      val page = BrowserSettingsConfigurable()
      val component = page.createComponent()!!
      page.reset()
      try {
        // the "Default Browser" dropdown is the first combo box of the page
        val comboBox = UIUtil.findComponentsOfType(component, JComboBox::class.java).first() as JComboBox<BrowserChoice>
        action(page, comboBox)
      }
      finally {
        page.disposeUIResources()
      }
    }
  }
}
