// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.productMigration

import com.intellij.openapi.util.io.IoTestUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

class MigrationLinuxDesktopEntryTest {
  @get:Rule
  val tempDir = TemporaryFolder()

  private val app: Path by lazy { tempDir.root.toPath().resolve("JetBrains/unified-ide-271.100") }
  private val applications: Path by lazy { tempDir.root.toPath().resolve("applications") }
  private val file: Path by lazy { applications.resolve("jetbrains-ide.desktop") }

  /** The entry is only for Linux. The tests have POSIX paths and file permissions. */
  @Before
  fun onlyOnUnix() {
    IoTestUtil.assumeUnix()
  }

  @Test
  fun `entry has the name, the icon, the launcher and the window class`() {
    val home = Path.of("/home/me/.local/share/JetBrains/unified-ide-271.3808")
    val text = linuxDesktopEntry("JetBrains IDE", home.resolve("bin/jetbrains"), home.resolve("bin/jetbrains.svg"), "jetbrains-ide")
    assertEquals("""
      [Desktop Entry]
      Version=1.0
      Type=Application
      Name=JetBrains IDE
      Icon=${home}/bin/jetbrains.svg
      Exec="${home}/bin/jetbrains" %f
      TryExec=${home}/bin/jetbrains
      Categories=Development;IDE;
      Terminal=false
      StartupWMClass=jetbrains-ide
      StartupNotify=true

      """.trimIndent(), text)
  }

  @Test
  fun `entry without an icon and a window class has no line for them`() {
    val text = linuxDesktopEntry("JetBrains IDE", Path.of("/opt/ide/bin/jetbrains"), icon = null, wmClass = null)!!
    assertFalse(text, text.contains("Icon="))
    assertFalse(text, text.contains("StartupWMClass="))
    assertTrue(text, text.contains("TryExec=/opt/ide/bin/jetbrains\n"))
  }

  @Test
  fun `reserved characters of a path have an escape`() {
    IoTestUtil.assumeUnix()
    val launcher = Path.of("/home/my name/a\"b`c\$d\\e%f/bin/jetbrains")
    val text = linuxDesktopEntry("IDE \\ name", launcher, icon = Path.of("/home/my name/a\\b.svg"), wmClass = null)!!
    val lines = text.lines()
    assertTrue(text, lines.contains("Name=IDE \\\\ name"))
    assertTrue(text, lines.contains("Icon=/home/my name/a\\\\b.svg"))
    assertTrue(text, lines.contains("Exec=\"/home/my name/a\\\\\"b\\\\`c\\\\\$d\\\\\\\\e%%f/bin/jetbrains\" %f"))
    assertTrue(text, lines.contains("TryExec=/home/my name/a\"b`c\$d\\\\e%f/bin/jetbrains"))
  }

  @Test
  fun `value with a control character gives no entry`() {
    val launcher = Path.of("/opt/ide/bin/jetbrains")
    assertNull(linuxDesktopEntry("JetBrains\nIDE", launcher, icon = null, wmClass = null))
    assertNull(linuxDesktopEntry("JetBrains IDE", launcher, icon = null, wmClass = "a\tb"))
    IoTestUtil.assumeUnix()
    assertNull(linuxDesktopEntry("JetBrains IDE", Path.of("/opt/i\nde/bin/jetbrains"), icon = null, wmClass = null))
  }

  @Test
  fun `file name is the window class, or it has the product code`() {
    assertEquals("jetbrains-ide.desktop", linuxDesktopEntryFileName("jetbrains-ide", "JB"))
    assertEquals("jetbrains-jb.desktop", linuxDesktopEntryFileName(null, "JB"))
    assertNull(linuxDesktopEntryFileName("../evil", "JB"))
    assertNull(linuxDesktopEntryFileName("a b", "JB"))
    assertNull(linuxDesktopEntryFileName(".hidden", "JB"))
    assertNull(linuxDesktopEntryFileName(null, "J/B"))
  }

  @Test
  fun `entry of an installed application is written into a new folder`() {
    val launcher = writeApp()
    writeLinuxDesktopEntry(app, "JB", applications)
    assertEquals(linuxDesktopEntry("JetBrains IDE", launcher, app.resolve("bin/jetbrains.svg"), "jetbrains-ide"), file.readText())
    assertEquals(listOf("jetbrains-ide.desktop"), applications.listDirectoryEntries().map { it.name })
  }

  @Test
  fun `file with a launcher that exists, with a launcher name and with no TryExec does not change`() {
    writeApp()
    val other = tempDir.newFile("other-launcher").toPath()
    val texts = listOf(
      "[Desktop Entry]\nName=Mine\nTryExec=${other}\n",
      "[Desktop Entry]\nName=Mine\nTryExec=missing-launcher\n",
      "[Desktop Entry]\nName=Mine\nExec=/missing\n",
    )
    for (text in texts) {
      applications.createDirectories()
      file.writeText(text)
      writeLinuxDesktopEntry(app, "JB", applications)
      assertEquals(text, file.readText())
    }
  }

  @Test
  fun `file with a dead TryExec in another group, and with an escape in the path of a launcher that exists, does not change`() {
    writeApp()
    val other = tempDir.newFolder("my folder").toPath().resolve("launcher").createFile()
    val dead = tempDir.root.toPath().resolve("removed/bin/jetbrains")
    val texts = listOf(
      "[Desktop Action New]\nTryExec=${dead}\n[Desktop Entry]\nName=Mine\nTryExec=${other}\n[Desktop Action Old]\nTryExec=${dead}\n",
      "[Desktop Entry]\nName=Mine\n[Desktop Action Old]\nTryExec=${dead}\n",
      "[Desktop Entry]\nName=Mine\nTryExec=${other.toString().replace(" ", "\\s")}\n",
      "[Desktop Entry]\nName=Mine\nTryExec=${dead}\\q\n",
    )
    for (text in texts) {
      applications.createDirectories()
      file.writeText(text)
      writeLinuxDesktopEntry(app, "JB", applications)
      assertEquals(text, file.readText())
    }
  }

  @Test
  fun `file with a launcher that does not exist is replaced`() {
    val launcher = writeApp()
    applications.createDirectories()
    file.writeText("[Desktop Entry]\nName=Old\nTryExec=${tempDir.root.toPath().resolve("removed/bin/jetbrains")}\n")
    writeLinuxDesktopEntry(app, "JB", applications)
    assertEquals(linuxDesktopEntry("JetBrains IDE", launcher, app.resolve("bin/jetbrains.svg"), "jetbrains-ide"), file.readText())
  }

  @Test
  fun `application with no name or no launcher gets no entry`() {
    writeRootLauncher(app, osName = "Linux", launcherPath = "bin/jetbrains")
    writeLinuxDesktopEntry(app, "JB", applications)
    writeRootLauncher(app, osName = "Windows", launcherPath = "bin/jetbrains64.exe")
    writeLinuxDesktopEntry(app, "JB", applications)
    writeLinuxDesktopEntry(tempDir.root.toPath().resolve("missing"), "JB", applications)
    assertFalse(applications.exists())
  }

  private fun writeApp(): Path {
    val launcher = writeRootLauncher(app, osName = "Linux", launcherPath = "bin/jetbrains")
    app.resolve("bin/jetbrains.svg").writeText("<svg/>")
    app.resolve("product-info.json").writeText("""{ "name": "JetBrains IDE", "productCode": "JB", "svgIconPath": "bin/jetbrains.svg",
      "launch": [{ "os": "Linux", "arch": "amd64", "launcherPath": "bin/jetbrains", "startupWmClass": "jetbrains-ide" }] }""")
    return launcher
  }
}
