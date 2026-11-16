// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests which `jetbrains://` links [BrowserLauncherImpl] runs in this IDE rather than in a browser.
 *
 * A link this IDE runs is checked through [JBProtocolCommand.hasCommand] only: running it needs a non-headless IDE.
 */
@TestApplication
class BrowserLauncherProtocolCommandTest {
  private class TestCommand : JBProtocolCommand(TEST_COMMAND) {
    override suspend fun execute(target: String?, parameters: Map<String, String>, fragment: String?): String? = null
  }

  private class RecordingOpener : UrlOpener() {
    val reached = mutableListOf<String>()

    override fun openUrl(browser: WebBrowser, url: String, project: Project?): Boolean {
      reached += url
      return true
    }
  }

  private val opener = RecordingOpener()

  @BeforeEach
  fun registerExtensions(@TestDisposable disposable: Disposable) {
    ExtensionPointName<JBProtocolCommand>("com.intellij.jbProtocolCommand").point.registerExtension(TestCommand(), disposable)
    UrlOpener.EP_NAME.point.registerExtension(opener, disposable)
  }

  @Test
  fun `a link of a command this IDE has runs here`() {
    assertTrue(JBProtocolCommand.hasCommand("open/$TEST_COMMAND/target?id=1"))
    assertTrue(JBProtocolCommand.hasCommand("idea/$TEST_COMMAND"))
  }

  @Test
  fun `a link of a command this IDE lacks reaches the browser`() {
    val link = "jetbrains://open/missing_command/target"
    assertFalse(JBProtocolCommand.hasCommand("open/missing_command/target"))

    BrowserLauncherImpl().browse(link, WebBrowserManager.getInstance().browsers.first(), project = null)

    assertEquals(listOf(link), opener.reached)
  }

  @Test
  fun `a link without a command name reaches the browser`() {
    assertFalse(JBProtocolCommand.hasCommand("open"))
  }
}

private const val TEST_COMMAND = "browser_launcher_test"
