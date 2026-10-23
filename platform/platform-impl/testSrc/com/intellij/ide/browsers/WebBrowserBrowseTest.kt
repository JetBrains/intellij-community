// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.swing.Icon

/**
 * Tests that [BrowserLauncherImpl] opens a link with [WebBrowser.browse] after its common steps.
 */
@TestApplication
class WebBrowserBrowseTest {
  private val launcher = BrowserLauncherImpl()

  private class RecordingBrowser : WebBrowser() {
    val opened = mutableListOf<String>()

    override fun getName(): String = "Recording"

    override fun getId(): UUID = UUID.fromString("3b9d0c1e-2a4f-4d6b-8e7a-5f1c0d9e8b26")

    override fun getFamily(): BrowserFamily? = null

    override fun getIcon(): Icon = SystemDefaultBrowser.icon

    override fun getPath(): String? = null

    override fun getBrowserNotFoundMessage(): String = "not found"

    override fun getSpecificSettings(): BrowserSpecificSettings? = null

    override fun browse(url: String, project: Project?) {
      opened += url
    }
  }

  @Test
  fun `launcher opens a link with the browse of the browser`() {
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

  private companion object {
    const val HTTP_URL = "http://localhost:3000/"
    const val FILE_URL = "file:///tmp/page.html"
  }
}
