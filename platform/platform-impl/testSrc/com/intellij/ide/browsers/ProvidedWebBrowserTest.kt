// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.ide.GeneralLocalSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.jdom.Element
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.swing.Icon

/**
 * Tests an [EditorTabWebBrowser] in [WebBrowserManager] and in [BrowserLauncherImpl].
 */
@TestApplication
class ProvidedWebBrowserTest {
  @TestDisposable
  lateinit var disposable: Disposable

  private val browser = RecordingBrowser(RecordingBrowser.ID)
  private val provider = FakeWebBrowserProvider(browser)
  private val launcher = BrowserLauncherImpl()

  private lateinit var savedState: Element
  private var savedUseDefaultBrowser = true
  private var savedBrowserPath = ""

  @BeforeEach
  fun setUp() {
    savedState = WebBrowserManager.getInstance().state
    savedUseDefaultBrowser = GeneralLocalSettings.getInstance().useDefaultBrowser
    savedBrowserPath = GeneralLocalSettings.getInstance().browserPath
    ExtensionTestUtil.maskExtensions(EditorTabWebBrowserProvider.EP_NAME, listOf(provider), disposable)
  }

  @AfterEach
  fun tearDown() {
    WebBrowserManager.getInstance().loadState(savedState)
    GeneralLocalSettings.getInstance().useDefaultBrowser = savedUseDefaultBrowser
    GeneralLocalSettings.getInstance().browserPath = savedBrowserPath
  }

  @Test
  fun `picked provided browser is the default browser while it is available`() {
    val manager = WebBrowserManager.getInstance()
    manager.setDefaultBrowserId(browser.id)

    assertTrue(manager.defaultBrowser === browser)
    assertTrue(manager.findBrowserById(browser.id.toString()) === browser)
    assertTrue(manager.browsers.none { it === browser })
    assertEquals(browser.id.toString(), manager.state.getAttributeValue("defaultBrowser"))
    assertTrue(GeneralLocalSettings.getInstance().useDefaultBrowser)
  }

  @Test
  fun `picked provided browser gives the system default browser while it is not available`() {
    val manager = WebBrowserManager.getInstance()
    manager.setDefaultBrowserId(browser.id)
    provider.available = false

    assertTrue(manager.defaultBrowser === SystemDefaultBrowser)
    assertNull(manager.findBrowserById(browser.id.toString()))
    assertEquals(browser.id, manager.defaultBrowserId)
  }

  @Test
  fun `link without a browser opens in the provided default browser`() {
    WebBrowserManager.getInstance().setDefaultBrowserId(browser.id)

    launcher.browse(HTTP_URL, null, null)
    launcher.browse(FILE_URL, null, null)

    assertEquals(listOf(HTTP_URL, FILE_URL), browser.opened)
  }

  @Test
  fun `provided browser cannot take the id of the system default browser or of a browser of the list`() {
    val chromeId = WebBrowserManager.PREDEFINED_CHROME_ID
    val manager = WebBrowserManager.getInstance()

    provider.providedBrowser = RecordingBrowser(SystemDefaultBrowser.id)
    assertTrue(manager.findBrowserById(SystemDefaultBrowser.id.toString()) === SystemDefaultBrowser)

    provider.providedBrowser = RecordingBrowser(chromeId)
    assertTrue(manager.findBrowserById(chromeId.toString()) in manager.browsers)
    manager.setDefaultBrowserId(chromeId)
    assertTrue(manager.defaultBrowser in manager.browsers)
    assertEquals(chromeId, manager.defaultBrowser.id)
  }

  private class RecordingBrowser(private val id: UUID) : EditorTabWebBrowser() {
    val opened = mutableListOf<String>()

    override fun getName(): String = "Recording"

    override fun getId(): UUID = id

    override fun getIcon(): Icon = SystemDefaultBrowser.icon

    override fun getBrowserNotFoundMessage(): String = "not found"

    override fun browse(url: String, project: Project?) {
      opened += url
    }

    override fun createEditorTabFile(project: Project, url: String): VirtualFile = LightVirtualFile(url)

    companion object {
      val ID: UUID = UUID.fromString("7c1d2e3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f")
    }
  }

  private class FakeWebBrowserProvider(var providedBrowser: EditorTabWebBrowser) : EditorTabWebBrowserProvider {
    var available = true

    override val browser: EditorTabWebBrowser?
      get() = if (available) providedBrowser else null
  }

  private companion object {
    const val HTTP_URL = "http://localhost:3000/"
    const val FILE_URL = "file:///tmp/page.html"
  }
}
