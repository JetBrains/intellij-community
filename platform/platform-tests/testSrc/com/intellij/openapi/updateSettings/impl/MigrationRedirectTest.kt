// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.application.MigrationImageData
import com.intellij.openapi.application.MigrationOpenRequests
import com.intellij.openapi.application.MigrationRedirect
import com.intellij.openapi.application.MigrationRedirectChoice
import com.intellij.openapi.application.MigrationRedirectContent
import com.intellij.openapi.application.MigrationRedirectStart
import com.intellij.openapi.application.MigrationState
import com.intellij.openapi.application.MigrationStatus
import com.intellij.openapi.application.launchMigrationRedirect
import com.intellij.openapi.application.loadMigrationRedirect
import com.intellij.openapi.application.migrationRedirectArguments
import com.intellij.openapi.application.migrationRedirectCommand
import com.intellij.openapi.application.migrationRedirectContentFile
import com.intellij.openapi.application.migrationStateFile
import com.intellij.openapi.application.showMigrationRedirect
import com.intellij.openapi.application.writeMigrationRedirectContent
import com.intellij.openapi.application.writeMigrationState
import com.intellij.openapi.util.io.IoTestUtil
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
import kotlin.io.path.createFile
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
    assertNull(load(MigrationRedirectStart.OPEN))
  }

  @Test
  fun `installed state gives no redirect and keeps the files`() {
    // a check of the application would delete the files, because the product code differs
    writeProductInfo(app, productCode = "GO")
    writeState(MigrationStatus.INSTALLED)
    writeContent()
    for (start in MigrationRedirectStart.entries) {
      assertNull(load(start))
    }
    assertTrue(stateFile.exists())
    assertTrue(contentFile.exists())
  }

  @Test
  fun `open start without a copy gives no window and keeps the state`() {
    writeState(MigrationStatus.MOVED)
    val redirect = load(MigrationRedirectStart.OPEN)!!
    assertEquals(app, redirect.state.app)
    assertNull(redirect.content)
    assertFalse(redirect.waits)
    assertTrue(stateFile.exists())
  }

  @Test
  fun `open start with an invalid copy gives no window`() {
    writeState(MigrationStatus.MOVED)
    contentFile.writeText("""{ "version": 1 }""")
    assertNull(load(MigrationRedirectStart.OPEN)!!.content)
    assertTrue(stateFile.exists())
  }

  @Test
  fun `open start with a valid copy gives the window`() {
    writeState(MigrationStatus.MOVED)
    writeContent()
    val redirect = load(MigrationRedirectStart.OPEN)!!
    assertEquals(MigrationStatus.MOVED, redirect.state.status)
    assertEquals("GoLand is now part of Unified IDE", redirect.content!!.texts.title)
  }

  @Test
  fun `start that waits gives no window and keeps the copy`() {
    writeState(MigrationStatus.MOVED)
    writeContent()
    for (start in listOf(MigrationRedirectStart.OPEN_AND_WAIT, MigrationRedirectStart.GUI_COMMAND)) {
      val redirect = load(start)!!
      assertTrue(redirect.waits)
      assertNull(redirect.content)
      assertNull("the application has no launcher", redirect.launcher)
    }
    assertTrue(contentFile.exists())
  }

  @Test
  fun `missing application deletes the state and the copy`() {
    writeState(MigrationStatus.MOVED, app = tempDir.root.toPath().resolve("Missing.app"))
    writeContent()
    assertNull(load(MigrationRedirectStart.OPEN))
    assertFalse(stateFile.exists())
    assertFalse(contentFile.exists())
  }

  @Test
  fun `start that is not redirected reads no file`() {
    writeState(MigrationStatus.MOVED, app = tempDir.root.toPath().resolve("Missing.app"))
    writeContent()
    assertNull(load(null))
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
    val content = load(MigrationRedirectStart.OPEN)?.content
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
  fun `launcher command runs the launcher with the redirect reason first`() {
    val launcher = Path.of("/Users/me/Applications/Unified IDE.app/Contents/MacOS/light")
    val command = migrationRedirectCommand(Path.of("/Users/me/Applications/Unified IDE.app"), "GO", listOf("--wait", "/a"), launcher)
    assertEquals(listOf(
      launcher.toString(),
      "-Dide.migration.version=1",
      "-Dide.migration.reason=redirect",
      "-Dide.migration.source.product=GO",
      "--wait",
      "/a",
    ), command)
  }

  @Test
  fun `forwarded arguments have no property of this IDE`() {
    val args = listOf("nosplash", "/a", "-Didea.config.path=/x", "-Dfoo", "--wait", "-Da=b=c", "/b")
    assertEquals(listOf("nosplash", "/a", "-Dfoo", "--wait", "/b"), migrationRedirectArguments(args, tempDir.root.toPath()))
  }

  @Test
  fun `forwarded arguments are absolute`() {
    val currentDir = tempDir.root.toPath().resolve("work").createDirectories()
    val file = currentDir.resolve("main.go").createFile()
    val a = currentDir.resolve("a").createFile()
    val b = currentDir.resolve("b").createFile()
    // a file with the name of the command does not change the command
    currentDir.resolve("diff").createFile()

    assertEquals(listOf(file.toString()), migrationRedirectArguments(listOf("main.go"), currentDir))
    assertEquals(listOf(currentDir.toString()), migrationRedirectArguments(listOf("."), currentDir))
    assertEquals(listOf("diff", a.toString(), b.toString()), migrationRedirectArguments(listOf("diff", "a", "b"), currentDir))
    assertEquals(listOf("-l", "10", file.toString(), "--wait"),
                 migrationRedirectArguments(listOf("-l", "10", "main.go", "--wait"), currentDir))
    assertEquals(listOf("missing.go", "jetbrains://idea/navigate"),
                 migrationRedirectArguments(listOf("missing.go", "jetbrains://idea/navigate"), currentDir))
  }

  @Test
  fun `open requests give the URL first and absolute paths`() {
    val file = tempDir.root.toPath().resolve("a.go")
    assertEquals(listOf("jetbrains://idea/navigate", file.toString()),
                 MigrationOpenRequests(listOf(file), "jetbrains://idea/navigate").toArguments())
    assertEquals(emptyList<String>(), MigrationOpenRequests(emptyList(), null).toArguments())
  }

  @Test
  fun `failed start returns null`() {
    // `open` fails for a missing application, and a system without `open` cannot start the command
    val state = MigrationState(MigrationStatus.MOVED, tempDir.root.toPath().resolve("Missing.app"), "JB")
    assertNull(launch(MigrationRedirect(state, waits = false, content = null, launcher = null), emptyList()))
  }

  @Test
  fun `waiting launch without a launcher returns null`() {
    val state = writeState(MigrationStatus.MOVED)
    var isStarted = false
    val redirect = MigrationRedirect(state, waits = true, content = null, launcher = null)
    assertNull(launch(redirect, listOf("--wait", "/a")) { isStarted = true })
    assertFalse(isStarted)
  }

  @Test
  fun `waiting launch returns the exit code of the child`() {
    IoTestUtil.assumeUnix()
    val launcher = writeMacLauncher(app, script = "#!/bin/sh\nexit 3\n")
    writeState(MigrationStatus.MOVED)
    val redirect = load(MigrationRedirectStart.OPEN_AND_WAIT)!!
    assertEquals(launcher, redirect.launcher)
    var isStarted = false
    assertEquals(3, launch(redirect, listOf("--wait", "/a")) { isStarted = true })
    assertTrue(isStarted)
  }

  @Test
  fun `waiting launch with a child that cannot start returns null`() {
    IoTestUtil.assumeUnix()
    val launcher = writeMacLauncher(app, script = "#!/missing/interpreter\n")
    val state = writeState(MigrationStatus.MOVED)
    var isStarted = false
    assertNull(launch(MigrationRedirect(state, waits = true, content = null, launcher), listOf("--wait", "/a")) { isStarted = true })
    assertFalse(isStarted)
  }

  private fun load(start: MigrationRedirectStart?): MigrationRedirect? = loadMigrationRedirect(start, stateFile)

  private fun loadContent(): MigrationRedirectContent = load(MigrationRedirectStart.OPEN)!!.content!!

  private fun launch(redirect: MigrationRedirect, arguments: List<String>, onStart: () -> Unit = {}): Int? = timeoutRunBlocking {
    launchMigrationRedirect(redirect, "GO", arguments, onStart = { onStart() })
  }

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
