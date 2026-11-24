// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.productMigration

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.system.OS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val LAUNCHER = "bin/jetbrains64.exe"

class MigrationWindowsInstallerTest {
  @get:Rule
  val tempDir = TemporaryFolder()

  private val workDir: Path by lazy { tempDir.newFolder("work").toPath() }
  private val target: Path by lazy { tempDir.root.toPath().resolve("Programs").resolve("JetBrains") }
  private val installer: Path by lazy { workDir.resolve("distribution.exe").also { it.writeText("the installer") } }
  private val removed = ArrayList<Path>()

  @Test
  fun `arguments of each path form have no space and give the text of the options`() {
    val forms = listOf(
      "C:\\Users\\me\\AppData\\Local",
      "C:\\Users\\John Smith\\AppData\\Local",
      "C:\\Users\\me\\App Data\\Local Files",
      "C:\\Users\\Лайт é\\AppData\\Local",
    )
    for (root in forms) {
      val config = Path.of("${root}\\Temp\\silent.config")
      val log = Path.of("${root}\\Temp\\install.log")
      val target = Path.of("${root}\\Programs\\JetBrains")
      val arguments = windowsInstallerArguments(config, log, target)!!
      // Java puts quotes around an argument that is empty or has a space, and the installer rejects the quotes
      assertTrue(root, arguments.none { it.isEmpty() || ' ' in it })
      assertEquals("/S /NCRC /CONFIG=${config} /LOG=${log} /D=${target}", arguments.joinToString(" "))
    }
  }

  @Test
  fun `option with spaces is split at each space`() {
    val arguments = windowsInstallerArguments(Path.of("C:\\a b\\silent.config"), Path.of("C:\\a b\\install.log"), Path.of("C:\\a b\\Jet Brains IDE"))
    assertEquals(listOf("/S", "/NCRC", "/CONFIG=C:\\a", "b\\silent.config", "/LOG=C:\\a", "b\\install.log", "/D=C:\\a", "b\\Jet", "Brains", "IDE"),
                 arguments)
  }

  @Test
  fun `path with two spaces in a row, a tab or a quote has no arguments`() {
    val good = Path.of("C:\\Users\\me\\x")
    val twoSpaces = Path.of("C:\\Users\\John  Smith\\x")
    assertNull(windowsInstallerArguments(twoSpaces, good, good))
    assertNull(windowsInstallerArguments(good, twoSpaces, good))
    assertNull(windowsInstallerArguments(good, good, twoSpaces))

    for (text in listOf("C:\\Users\\John\tSmith\\x", "C:\\Users\\John\"Smith\\x", "C:\\Users\\me\\x ")) {
      val bad = try {
        Path.of(text)
      }
      catch (_: InvalidPathException) {
        // Windows has no such path
        continue
      }
      assertNull(text, windowsInstallerArguments(bad, good, good))
      assertNull(text, windowsInstallerArguments(good, bad, good))
      assertNull(text, windowsInstallerArguments(good, good, bad))
    }
  }

  @Test
  fun `silent config has only the user mode`() {
    assertEquals(listOf("mode=user"), WINDOWS_SILENT_CONFIG.lines().filter { it.isNotEmpty() })
  }

  @Test
  fun `download file name has the file type of the OS`() {
    assertEquals("distribution.exe", migrationDownloadFileName(OS.Windows))
    assertEquals("distribution.dmg", migrationDownloadFileName(OS.macOS))
  }

  @Test
  fun `missing folder gets the install`() {
    var config: String? = null
    var started: Pair<Path, List<String>>? = null
    val result = install(runInstaller = { file, arguments ->
      config = workDir.resolve("silent.config").readText()
      started = file to arguments
      writeRootLauncher(target, osName = "Windows", launcherPath = LAUNCHER)
      0
    })
    assertEquals(target, result)
    assertEquals(WINDOWS_SILENT_CONFIG, config)
    assertEquals(installer to windowsInstallerArguments(workDir.resolve("silent.config"), workDir.resolve("install.log"), target), started)
    assertEquals(emptyList<Path>(), removed)
  }

  @Test
  fun `empty folder gets the install`() {
    target.createDirectories()
    assertEquals(target, install())
    assertEquals(emptyList<Path>(), removed)
  }

  @Test
  fun `installed application is used with no download and no installer`() {
    val launcher = writeRootLauncher(target, osName = "Windows", launcherPath = LAUNCHER)
    val before = snapshot(target)
    val checked = ArrayList<Path>()
    val result = install(
      isTrusted = {
        checked.add(it)
        true
      },
      download = { error("no download") },
      runInstaller = { _, _ -> error("no installer") },
    )
    assertEquals(target, result)
    assertEquals(listOf(launcher), checked)
    assertEquals(before, snapshot(target))
  }

  @Test
  fun `application that another IDE installs during the download is used`() {
    val result = install(
      download = {
        writeRootLauncher(target, osName = "Windows", launcherPath = LAUNCHER)
        installer
      },
      runInstaller = { _, _ -> error("no installer") },
    )
    assertEquals(target, result)
    assertEquals(emptyList<Path>(), removed)
  }

  @Test
  fun `folder with other content gives the error and does not change`() {
    target.createDirectories().resolve("notes.txt").writeText("a file of the user")
    assertExistingFolderError()
  }

  @Test
  fun `application of another product gives the error and does not change`() {
    writeRootLauncher(target, osName = "Windows", launcherPath = LAUNCHER, productCode = "GO")
    assertExistingFolderError()
  }

  @Test
  fun `application with no launcher gives the error and does not change`() {
    target.createDirectories().resolve("product-info.json").writeText("""{ "productCode": "JB" }""")
    assertExistingFolderError()
  }

  @Test
  fun `application with a launcher that fails the check gives the error and does not change`() {
    val launcher = writeRootLauncher(target, osName = "Windows", launcherPath = LAUNCHER)
    val checked = ArrayList<Path>()
    assertExistingFolderError(isTrusted = {
      checked.add(it)
      false
    })
    assertEquals(listOf(launcher), checked)
  }

  @Test
  fun `path that the installer cannot read gives the install error before the download`() {
    val spaced = tempDir.root.toPath().resolve("John  Smith").resolve("JetBrains")
    val error = assertThrows(MigrationInstallException::class.java) {
      install(target = spaced, isTrusted = { error("no check") }, download = { error("no download") }, runInstaller = { _, _ -> error("no installer") })
    }
    assertEquals(IdeBundle.message("updates.migration.error.install"), error.userMessage)
    assertFalse(spaced.exists())
    assertEquals(emptyList<Path>(), removed)
  }

  @Test
  fun `rejected installer never runs`() {
    val error = assertThrows(MigrationInstallException::class.java) {
      install(isTrusted = { false }, runInstaller = { _, _ -> error("no installer") })
    }
    assertEquals(IdeBundle.message("updates.migration.error.install"), error.userMessage)
    assertFalse(target.exists())
    assertEquals(emptyList<Path>(), removed)
  }

  @Test
  fun `exit code that is not 0 gives the install error and the cleanup`() {
    assertFailedInstall {
      writeRootLauncher(target, osName = "Windows", launcherPath = LAUNCHER)
      2
    }
  }

  @Test
  fun `exit code 0 with no application in the folder gives the install error and the cleanup`() {
    assertFailedInstall { 0 }
  }

  @Test
  fun `exit code 0 with another product gives the install error and the cleanup`() {
    assertFailedInstall {
      writeRootLauncher(target, osName = "Windows", launcherPath = LAUNCHER, productCode = "GO")
      0
    }
  }

  @Test
  fun `cancel of the installer runs the cleanup`() {
    assertThrows(ProcessCanceledException::class.java) {
      install(runInstaller = { _, _ ->
        target.createDirectories().resolve("partial.jar").writeText("a part of the install")
        throw ProcessCanceledException()
      })
    }
    assertEquals(listOf(target), removed)
  }

  @Test
  fun `removal of an install that did not end deletes the folder`() {
    target.resolve("lib").createDirectories().resolve("partial.jar").writeText("a part of the install")
    removeFailedWindowsInstall(target)
    assertFalse(target.exists())
    assertTrue(target.parent.exists())
  }

  private fun assertFailedInstall(installerResult: () -> Int) {
    val error = assertThrows(MigrationInstallException::class.java) { install(runInstaller = { _, _ -> installerResult() }) }
    assertEquals(IdeBundle.message("updates.migration.error.install"), error.userMessage)
    assertEquals(listOf(target), removed)
  }

  private fun assertExistingFolderError(isTrusted: (Path) -> Boolean = { true }) {
    val before = snapshot(target)
    val error = assertThrows(MigrationInstallException::class.java) {
      install(isTrusted = isTrusted, download = { error("no download") }, runInstaller = { _, _ -> error("no installer") })
    }
    assertEquals(IdeBundle.message("updates.migration.error.exists", target.toString()), error.userMessage)
    assertEquals(before, snapshot(target))
    assertEquals(emptyList<Path>(), removed)
  }

  private fun install(
    target: Path = this.target,
    isTrusted: (Path) -> Boolean = { true },
    download: () -> Path = { installer },
    runInstaller: (Path, List<String>) -> Int = { _, _ ->
      writeRootLauncher(target, osName = "Windows", launcherPath = LAUNCHER)
      0
    },
  ): Path = installWindowsApp(target, "JB", workDir, EmptyProgressIndicator(ModalityState.nonModal()),
                              isTrusted, download, runInstaller, removeFailedInstall = { removed.add(it) })
}
