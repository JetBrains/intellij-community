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
 * A link this IDE runs is checked through [JBProtocolCommand.runsWhereClicked] only: running it needs a non-headless IDE.
 */
@TestApplication
class BrowserLauncherProtocolCommandTest {
  private class DefaultCommand : JBProtocolCommand(DEFAULT_COMMAND) {
    override suspend fun execute(target: String?, parameters: Map<String, String>, fragment: String?): String? = null
  }

  private class DaemonRoutedCommand : JBProtocolCommand(DAEMON_ROUTED_COMMAND) {
    override fun runsWhereClicked(target: String?, parameters: Map<String, String>): Boolean = false

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
    val commands = ExtensionPointName<JBProtocolCommand>("com.intellij.jbProtocolCommand").point
    commands.registerExtension(DefaultCommand(), disposable)
    commands.registerExtension(DaemonRoutedCommand(), disposable)
    UrlOpener.EP_NAME.point.registerExtension(opener, disposable)
  }

  @Test
  fun `a link of a command this IDE has runs here by default`() {
    assertTrue(JBProtocolCommand.runsWhereClicked("open/$DEFAULT_COMMAND/target?id=1"))
    assertTrue(JBProtocolCommand.runsWhereClicked("idea/$DEFAULT_COMMAND"))
  }

  @Test
  fun `a command can leave its link to the daemon`() {
    val link = "jetbrains://open/$DAEMON_ROUTED_COMMAND/target?id=1"
    assertFalse(JBProtocolCommand.runsWhereClicked("open/$DAEMON_ROUTED_COMMAND/target?id=1"))

    BrowserLauncherImpl().browse(link, WebBrowserManager.getInstance().browsers.first(), project = null)

    assertEquals(listOf(link), opener.reached)
  }

  @Test
  fun `a link of a command this IDE lacks reaches the browser`() {
    val link = "jetbrains://open/missing_command/target"
    assertFalse(JBProtocolCommand.runsWhereClicked("open/missing_command/target"))

    BrowserLauncherImpl().browse(link, WebBrowserManager.getInstance().browsers.first(), project = null)

    assertEquals(listOf(link), opener.reached)
  }

  @Test
  fun `a link without a command name reaches the browser`() {
    assertFalse(JBProtocolCommand.runsWhereClicked("open"))
  }
}

private const val DEFAULT_COMMAND = "browser_launcher_test_default"
private const val DAEMON_ROUTED_COMMAND = "browser_launcher_test_daemon_routed"
