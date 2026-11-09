// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.application.MigrationImageData
import com.intellij.openapi.application.MigrationRedirect
import com.intellij.openapi.application.MigrationRedirectChoice
import com.intellij.openapi.application.MigrationRedirectContent
import com.intellij.openapi.application.MigrationState
import com.intellij.openapi.application.MigrationStatus
import com.intellij.openapi.application.launchMigrationRedirect
import com.intellij.openapi.application.loadMigrationRedirect
import com.intellij.openapi.application.migrationRedirectCommand
import com.intellij.openapi.application.migrationRedirectContentFile
import com.intellij.openapi.application.migrationStateFile
import com.intellij.openapi.application.showMigrationRedirect
import com.intellij.openapi.application.writeMigrationRedirectContent
import com.intellij.openapi.application.writeMigrationState
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.runInEdtAndGet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

class MigrationRedirectTest {
  @get:Rule
  val tempDir = TemporaryFolder()

  private val configDir: Path by lazy { tempDir.root.toPath().resolve("config") }
  private val stateFile: Path by lazy { migrationStateFile(configDir) }
  private val contentFile: Path by lazy { migrationRedirectContentFile(stateFile) }
  private val app: Path by lazy { tempDir.root.toPath().resolve("Unified IDE.app").createDirectories() }

  @Before
  fun writeApp() {
    writeProductInfo(app)
  }

  @Test
  fun `no state gives no redirect`() {
    assertNull(load())
  }

  @Test
  fun `installed state gives no redirect and keeps the files`() {
    // a check of the application would delete the files, because the product code differs
    writeProductInfo(app, productCode = "GO")
    writeState(MigrationStatus.INSTALLED)
    writeContent()
    assertNull(load())
    assertTrue(stateFile.exists())
    assertTrue(contentFile.exists())
  }

  @Test
  fun `moved state without a copy gives no redirect and keeps the state`() {
    writeState(MigrationStatus.MOVED)
    assertNull(load())
    assertTrue(stateFile.exists())
  }

  @Test
  fun `moved state with an invalid copy gives no redirect`() {
    writeState(MigrationStatus.MOVED)
    contentFile.writeText("""{ "version": 1 }""")
    assertNull(load())
    assertTrue(stateFile.exists())
  }

  @Test
  fun `moved state with a valid copy gives a redirect`() {
    writeState(MigrationStatus.MOVED)
    writeContent()
    val redirect = load()!!
    assertEquals(app, redirect.state.app)
    assertEquals(MigrationStatus.MOVED, redirect.state.status)
    assertEquals("GoLand is now part of Unified IDE", redirect.content.texts.title)
  }

  @Test
  fun `missing application deletes the state and the copy`() {
    writeState(MigrationStatus.MOVED, app = tempDir.root.toPath().resolve("Missing.app"))
    writeContent()
    assertNull(load())
    assertFalse(stateFile.exists())
    assertFalse(contentFile.exists())
  }

  @Test
  fun `start that is not redirected reads no file`() {
    writeState(MigrationStatus.MOVED, app = tempDir.root.toPath().resolve("Missing.app"))
    writeContent()
    assertNull(loadMigrationRedirect(isRedirected = false, stateFile))
    assertTrue(stateFile.exists())
    assertTrue(contentFile.exists())
  }

  @Test
  fun `logo that fails to render gives launch and deletes the copy`() {
    writeState(MigrationStatus.MOVED)
    // the size is valid, but the logo of 32 pixels gets a height of 0 pixels, so the render throws
    val flat = """<svg xmlns="http://www.w3.org/2000/svg" width="100000" height="1"/>""".toByteArray()
    val base = redirectContent()
    writeMigrationRedirectContent(MigrationRedirectContent(base.texts, MigrationImageData(flat, flat), base.targetLogo), contentFile)
    val content = load()?.content
    assertNotNull("the copy passes the check of the SVG size", content)

    var isWindowShown = false
    val choice = show(content!!) { _, _, _ ->
      isWindowShown = true
      MigrationRedirectChoice.EXIT
    }
    assertEquals(MigrationRedirectChoice.LAUNCH, choice)
    assertFalse("the render fails before the window", isWindowShown)
    assertFalse(contentFile.exists())
    assertTrue(stateFile.exists())
  }

  @Test
  fun `window failure gives launch, deletes the copy and closes the look and feel`() {
    writeState(MigrationStatus.MOVED)
    writeContent()
    var isClosed = false
    val choice = show(loadContent(), setLookAndFeel = { AutoCloseable { isClosed = true } }) { _, _, _ ->
      throw IllegalStateException("the window cannot show")
    }
    assertEquals(MigrationRedirectChoice.LAUNCH, choice)
    assertTrue(isClosed)
    assertFalse(contentFile.exists())
    assertTrue(stateFile.exists())
  }

  @Test
  fun `look and feel is set before the window and closed after it`() {
    writeState(MigrationStatus.MOVED)
    writeContent()
    val calls = ArrayList<String>()
    val setLookAndFeel = {
      calls.add("look and feel")
      AutoCloseable { calls.add("close") }
    }
    show(loadContent(), setLookAndFeel) { _, _, _ ->
      calls.add("window")
      MigrationRedirectChoice.EXIT
    }
    assertEquals(listOf("look and feel", "window", "close"), calls)
  }

  @Test
  fun `close failure keeps the choice`() {
    writeState(MigrationStatus.MOVED)
    writeContent()
    val failingClose = AutoCloseable { throw IllegalStateException("the look and feel cannot be restored") }
    val choice = show(loadContent(), setLookAndFeel = { failingClose }) { _, _, _ -> MigrationRedirectChoice.EXIT }
    assertEquals("a failure of the cleanup does not change the choice", MigrationRedirectChoice.EXIT, choice)
    assertTrue(contentFile.exists())
  }

  @Test
  fun `look and feel failure gives launch and deletes the copy`() {
    writeState(MigrationStatus.MOVED)
    writeContent()
    var isWindowShown = false
    val choice = show(loadContent(), setLookAndFeel = { throw IllegalStateException("the look and feel cannot be set") }) { _, _, _ ->
      isWindowShown = true
      MigrationRedirectChoice.EXIT
    }
    assertEquals(MigrationRedirectChoice.LAUNCH, choice)
    assertFalse(isWindowShown)
    assertFalse(contentFile.exists())
    assertTrue(stateFile.exists())
  }

  @Test
  fun `close gives exit and keeps the state and the copy`() {
    writeState(MigrationStatus.MOVED)
    writeContent()
    val before = Files.readString(stateFile)
    assertEquals(MigrationRedirectChoice.EXIT, show(loadContent()) { _, _, _ -> MigrationRedirectChoice.EXIT })
    assertEquals(before, Files.readString(stateFile))
    assertTrue(contentFile.exists())
  }

  @Test
  fun `redirect command has the redirect reason and the arguments`() {
    val command = migrationRedirectCommand(Path.of("/Users/me/Applications/Unified IDE.app"), "GO", listOf("/Users/me/my project", "diff"))
    assertEquals(listOf(
      "/usr/bin/open", "-n", "-a", "/Users/me/Applications/Unified IDE.app", "--args",
      "-Dide.migration.version=1",
      "-Dide.migration.reason=redirect",
      "-Dide.migration.source.product=GO",
      "/Users/me/my project",
      "diff",
    ), command)
  }

  @Test
  fun `failed start returns false`() {
    // `open` fails for a missing application, and a system without `open` cannot start the command
    val state = MigrationState(MigrationStatus.MOVED, tempDir.root.toPath().resolve("Missing.app"), "JB")
    assertFalse(timeoutRunBlocking { launchMigrationRedirect(MigrationRedirect(state, redirectContent()), "GO", emptyList()) })
  }

  private fun load(): MigrationRedirect? = loadMigrationRedirect(isRedirected = true, stateFile)

  private fun loadContent(): MigrationRedirectContent = load()!!.content

  private fun show(
    content: MigrationRedirectContent,
    // the test JVM keeps its look and feel
    setLookAndFeel: () -> AutoCloseable = { AutoCloseable {} },
    showWindow: (MigrationRedirectContent, Icon, Icon) -> MigrationRedirectChoice,
  ): MigrationRedirectChoice = runInEdtAndGet {
    showMigrationRedirect(content, stateFile, setLookAndFeel, showWindow)
  }

  private fun writeState(status: MigrationStatus, app: Path = this.app): MigrationState {
    val state = MigrationState(status, app, "JB")
    writeMigrationState(state, stateFile)
    return state
  }

  private fun writeContent() {
    writeMigrationRedirectContent(redirectContent(), contentFile)
  }
}
