// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.ide.GeneralLocalSettings
import com.intellij.ide.browsers.BrowserLauncherAppless.Companion.canUseSystemDefaultBrowserPolicy
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import org.jdom.Element
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.swing.Icon
import javax.swing.JComboBox

/**
 * Tests the "Default Browser" dropdown of [BrowserSettingsConfigurable].
 */
@TestApplication
class DefaultBrowserDropdownTest {
  @TestDisposable
  lateinit var disposable: Disposable

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

  @Test
  fun `dropdown offers a provided browser after the system default browser`() {
    loadState("""<state />""")
    val providedBrowser = withProvidedBrowser()

    withPage { page, comboBox ->
      val ids = (0 until comboBox.itemCount).map { comboBox.getItemAt(it).id }
      val expectedIndex = if (canUseSystemDefaultBrowserPolicy()) 1 else 0
      assertEquals(expectedIndex, ids.indexOf(providedBrowser.id))

      comboBox.selectedItem = comboBox.getItemAt(expectedIndex)
      page.apply()
      assertFalse(page.isModified)
    }

    assertTrue(WebBrowserManager.getInstance().defaultBrowser === providedBrowser)
    assertTrue(GeneralLocalSettings.getInstance().useDefaultBrowser)
  }

  @Test
  fun `dropdown has no provided browser without a provider`() {
    loadState("""<state defaultBrowser="$PROVIDED_BROWSER_ID" />""")

    withPage { page, comboBox ->
      val ids = (0 until comboBox.itemCount).map { comboBox.getItemAt(it).id }
      assertFalse(PROVIDED_BROWSER_ID in ids)
      assertFalse(page.isModified)
    }
  }

  @Test
  fun `dropdown selects the browser of the list when a provided browser takes its id`() {
    loadState("""<state defaultBrowser="$chromeId" />""")
    withProvidedBrowser(chromeId)

    withPage { page, comboBox ->
      val selected = comboBox.selectedItem as BrowserChoice
      assertEquals(chromeId, selected.id)
      assertEquals(WebBrowserManager.getInstance().browsers.first { it.id == chromeId }.name, selected.name)
      assertFalse(page.isModified)
    }
  }

  private fun withProvidedBrowser(id: UUID = PROVIDED_BROWSER_ID): WebBrowser {
    val providedBrowser = object : WebBrowser() {
      override fun getName(): String = "Provided"

      override fun getId(): UUID = id

      override fun getFamily(): BrowserFamily? = null

      override fun getIcon(): Icon = SystemDefaultBrowser.icon

      override fun getPath(): String? = null

      override fun getBrowserNotFoundMessage(): String = "not found"

      override fun getSpecificSettings(): BrowserSpecificSettings? = null

      override fun browse(url: String, project: Project?) {}
    }
    val provider = object : WebBrowserProvider {
      override val browser: WebBrowser = providedBrowser
    }
    ExtensionTestUtil.maskExtensions(WebBrowserProvider.EP_NAME, listOf(provider), disposable)
    return providedBrowser
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

  private companion object {
    val PROVIDED_BROWSER_ID: UUID = UUID.fromString("7c1d2e3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f")
  }
}
