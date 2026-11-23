// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.swing.Icon

/**
 * Tests that [BrowserLauncherImpl] selects a browser after its common steps.
 */
@TestApplication
class WebBrowserBrowseTest {
  private val launcher = object : BrowserLauncherImpl() {
    var allowBrowse = true
    var urlSuffix = ""
    val checked = mutableListOf<String>()

    override fun signUrl(url: String): String = super.signUrl(url) + urlSuffix

    override fun canBrowse(project: Project?, uri: String): Boolean {
      checked += uri
      return allowBrowse
    }
  }

  private class RecordingBrowser : EditorTabWebBrowser() {
    val opened = mutableListOf<String>()

    override fun getName(): String = "Recording"

    override fun getId(): UUID = UUID.fromString("3b9d0c1e-2a4f-4d6b-8e7a-5f1c0d9e8b26")

    override fun getIcon(): Icon = SystemDefaultBrowser.icon

    override fun getBrowserNotFoundMessage(): String = "not found"

    override fun browse(url: String, project: Project?) {
      opened += url
    }

    override fun createEditorTabFile(project: Project, url: String): VirtualFile = LightVirtualFile(url)
  }

  @Test
  fun `launcher selects an editor tab browser for HTTP and file links`() {
    val browser = RecordingBrowser()

    launcher.browse(HTTP_URL, browser, null)
    launcher.browse(FILE_URL, browser, null)

    assertEquals(listOf(HTTP_URL, FILE_URL), browser.opened)
  }

  @Test
  fun `launcher ignores a jar link`() {
    val browser = RecordingBrowser()

    launcher.browse("jar:file:///tmp/a.jar!/index.html", browser, null)

    assertEquals(emptyList<String>(), browser.opened)
  }

  @Test
  fun `launcher prepares the URL before the check and browser selection`() {
    val browser = RecordingBrowser()
    launcher.urlSuffix = "?signed"

    launcher.browse("  $HTTP_URL  ", browser, null)

    assertEquals(listOf("$HTTP_URL?signed"), launcher.checked)
    assertEquals(launcher.checked, browser.opened)
  }

  @Test
  fun `launcher does not open a link when the check rejects it`() {
    val browser = RecordingBrowser()
    launcher.allowBrowse = false

    launcher.browse(HTTP_URL, browser, null)

    assertEquals(listOf(HTTP_URL), launcher.checked)
    assertEquals(emptyList<String>(), browser.opened)
  }

  @Test
  fun `launcher reports a missing browser path`(@TestDisposable disposable: Disposable) {
    val browser = object : WebBrowser() {
      override fun getName(): String = "Missing"
      override fun getId(): UUID = UUID.randomUUID()
      override fun getFamily(): BrowserFamily? = null
      override fun getIcon(): Icon = SystemDefaultBrowser.icon
      override fun getPath(): String = " "
      override fun getBrowserNotFoundMessage(): String = "not found"
      override fun getSpecificSettings(): BrowserSpecificSettings? = null
    }
    val errors = mutableListOf<String?>()
    val launcher = object : BrowserLauncherAppless() {
      override fun showError(message: String?, project: Project?, browser: WebBrowser?, retry: (() -> Unit)?) {
        errors += message
      }
    }

    ApplicationManager.getApplication().replaceService(BrowserLauncher::class.java, launcher, disposable)

    launcher.browse(HTTP_URL, browser, null)

    assertEquals(listOf("not found"), errors)
  }

  @Test
  fun `external browser is the system default browser when the default browser shows pages in editor tabs`() {
    val launcher = SelectionLauncher(defaultBrowser = RecordingBrowser())

    launcher.browseInExternalBrowser(HTTP_URL, null)

    assertEquals(listOf<WebBrowser?>(SystemDefaultBrowser), launcher.selected)
  }

  @Test
  fun `external link names no browser when the default browser is outside the IDE`() {
    val launcher = SelectionLauncher(WebBrowserManager.getInstance().getFirstBrowser(BrowserFamily.FIREFOX))

    launcher.browseInExternalBrowser(HTTP_URL, null)

    assertEquals(listOf<WebBrowser?>(null), launcher.selected)
  }

  /** Records the browser that the launcher selects, and opens nothing. */
  private class SelectionLauncher(private val defaultBrowser: WebBrowser) : BrowserLauncherImpl() {
    val selected = mutableListOf<WebBrowser?>()

    override fun getDefaultBrowser(): WebBrowser = defaultBrowser

    override fun browse(url: String, browser: WebBrowser?, project: Project?) {
      selected += browser
    }
  }

  private companion object {
    const val HTTP_URL = "http://localhost:3000/"
    const val FILE_URL = "file:///tmp/page.html"
  }
}
