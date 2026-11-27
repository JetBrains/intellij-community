// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.productMigration

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.system.OS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.exists
import kotlin.io.path.isExecutable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readSymbolicLink
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val LAUNCHER = "bin/jetbrains"
private const val ROOT = "unified-ide-271.100"

class MigrationLinuxInstallerTest {
  @get:Rule
  val tempDir = TemporaryFolder()

  private val parent: Path by lazy { tempDir.newFolder("data").toPath().resolve("JetBrains").createDirectories() }
  private val source: Path by lazy { tempDir.newFolder("source").toPath() }
  private val archive: Path by lazy { tempDir.root.toPath().resolve("distribution.tar.gz").also { it.writeText("the archive") } }

  @Test
  fun `download file name has the file type of the OS`() {
    assertEquals("distribution.tar.gz", migrationDownloadFileName(OS.Linux))
    assertEquals("distribution.exe", migrationDownloadFileName(OS.Windows))
    assertEquals("distribution.dmg", migrationDownloadFileName(OS.macOS))
  }

  @Test
  fun `archive root becomes the install folder`() {
    writeApp(source.resolve(ROOT))
    val target = install()
    assertEquals(parent.resolve(ROOT), target)
    assertTrue(target.resolve(LAUNCHER).exists())
    assertEquals(listOf(ROOT), names(parent))
  }

  @Test
  fun `empty folder with the name of the root gets the install`() {
    IoTestUtil.assumeUnix()
    writeApp(source.resolve(ROOT))
    parent.resolve(ROOT).createDirectories()
    assertEquals(parent.resolve(ROOT), install())
    assertTrue(parent.resolve(ROOT).resolve(LAUNCHER).exists())
    assertEquals(listOf(ROOT), names(parent))
  }

  @Test
  fun `installed application in a folder with another name is used with no download`() {
    val installed = parent.resolve("unified-ide-270.5")
    writeApp(installed)
    val before = snapshot(parent)
    assertEquals(installed, install(download = { error("no download") }, unpack = { _, _ -> error("no unpack") }))
    assertEquals(before, snapshot(parent))
  }

  @Test
  fun `folders of another product and with no launcher are not used and do not change`() {
    writeApp(parent.resolve("goland"), product = "GO")
    writeRootLauncher(parent.resolve("windows-only"), osName = "Windows", launcherPath = "bin/jetbrains64.exe")
    parent.resolve("plugins").createDirectories().resolve("notes.txt").writeText("a file")
    val before = snapshot(parent).filterKeys { !it.startsWith(ROOT) }
    writeApp(source.resolve(ROOT))
    assertEquals(parent.resolve(ROOT), install())
    assertEquals(before, snapshot(parent).filterKeys { !it.startsWith(ROOT) })
  }

  @Test
  fun `from two installed applications the last name is used`() {
    writeApp(parent.resolve("unified-ide-270.5"))
    writeApp(parent.resolve("unified-ide-271.9"))
    writeApp(parent.resolve("other"), product = "GO")
    assertEquals(parent.resolve("unified-ide-271.9"), findInstalledLinuxApp(parent, "JB"))
    assertEquals(parent.resolve("other"), findInstalledLinuxApp(parent, "GO"))
    assertNull(findInstalledLinuxApp(parent, "PY"))
    assertNull(findInstalledLinuxApp(parent.resolve("missing"), "JB"))
  }

  @Test
  fun `application that another IDE installs during the download is used`() {
    val installed = parent.resolve("unified-ide-270.5")
    val result = install(
      download = {
        writeApp(installed)
        archive
      },
      unpack = { _, _ -> error("no unpack") },
    )
    assertEquals(installed, result)
    assertEquals(listOf("unified-ide-270.5"), names(parent))
  }

  @Test
  fun `folder with other content gives the error and does not change`() {
    writeApp(source.resolve(ROOT))
    val target = parent.resolve(ROOT).createDirectories()
    target.resolve("notes.txt").writeText("a file of the user")
    val before = snapshot(parent)
    val e = assertThrows(MigrationInstallException::class.java) { install() }
    assertEquals(IdeBundle.message("updates.migration.error.exists", target.toString()), e.userMessage)
    assertEquals(before, snapshot(parent))
  }

  @Test
  fun `archive without one root folder of the product gives the install error and nothing stays`() {
    val cases = listOf<(Path) -> Unit>(
      { },
      { writeApp(it.resolve(ROOT)); writeApp(it.resolve("second")) },
      { it.resolve(ROOT).writeText("a file") },
      { it.resolve(ROOT).createDirectories().resolve("notes.txt").writeText("no product info") },
      { writeApp(it.resolve(ROOT), product = "GO") },
      { writeRootLauncher(it.resolve(ROOT), osName = "Windows", launcherPath = "bin/jetbrains64.exe") },
    )
    for ((index, content) in cases.withIndex()) {
      NioFiles.deleteRecursively(source)
      content(source.createDirectories())
      val e = assertThrows("case ${index}", MigrationInstallException::class.java) { install() }
      assertEquals("case ${index}", IdeBundle.message("updates.migration.error.install"), e.userMessage)
      assertEquals("case ${index}", emptyList<String>(), names(parent))
    }
  }

  @Test
  fun `failure and cancel of the unpack leave no folder`() {
    assertThrows(MigrationInstallException::class.java) {
      install(unpack = { _, target ->
        target.resolve("part").writeText("a part")
        throw MigrationInstallException(IdeBundle.message("updates.migration.error.install"), "the unpack fails")
      })
    }
    assertEquals(emptyList<String>(), names(parent))
    assertThrows(ProcessCanceledException::class.java) {
      install(unpack = { _, target ->
        target.resolve("part").writeText("a part")
        throw ProcessCanceledException()
      })
    }
    assertEquals(emptyList<String>(), names(parent))
  }

  @Test
  fun `two installs at the same time do not share the partial folder`() {
    writeApp(source.resolve(ROOT))
    var second: Path? = null
    val first = install(unpack = { _, target ->
      copySource(target)
      second = install()
      // the first install still has its complete copy
      assertTrue(target.resolve(ROOT).resolve(LAUNCHER).exists())
    })
    assertEquals(parent.resolve(ROOT), second)
    assertEquals(parent.resolve(ROOT), first)
    assertTrue(first.resolve(LAUNCHER).exists())
    assertEquals(listOf(ROOT), names(parent))
  }

  @Test
  fun `partial folder that is older than a day is deleted, and a new one stays`() {
    writeApp(source.resolve(ROOT))
    val old = parent.resolve(".migration-partial-old").createDirectories()
    old.resolve("part").writeText("a part")
    Files.setLastModifiedTime(old, FileTime.fromMillis(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(25)))
    val live = parent.resolve(".migration-partial-live").createDirectories()
    install()
    assertFalse(old.exists())
    assertTrue(live.exists())
  }

  @Test
  fun `real archive keeps a relative link and an executable file`() {
    assumeTar()
    val root = source.resolve(ROOT)
    writeApp(root)
    root.resolve("lib").createDirectories().resolve("real.so").writeText("a library")
    root.resolve("lib/link.so").createSymbolicLinkPointingTo(Path.of("real.so"))
    root.resolve("notes.txt").writeText("not executable")
    val target = install(download = { tar(source, ROOT) }, unpack = ::unpack)
    assertEquals(parent.resolve(ROOT), target)
    assertEquals(Path.of("real.so"), target.resolve("lib/link.so").readSymbolicLink())
    assertEquals("a library", target.resolve("lib/link.so").readText())
    assertTrue(target.resolve(LAUNCHER).isExecutable())
    assertFalse(target.resolve("notes.txt").isExecutable())
    assertEquals(listOf(ROOT), names(parent))
  }

  @Test
  fun `cancel during the unpack of a real archive stops the install and nothing stays`() {
    assumeTar()
    writeApp(source.resolve(ROOT))
    // `cancel` of a real indicator needs an application
    val indicator = object : ProgressIndicator by EmptyProgressIndicator(ModalityState.nonModal()) {
      override fun isCanceled(): Boolean = true
      override fun checkCanceled() = throw ProcessCanceledException()
    }
    assertThrows(ProcessCanceledException::class.java) {
      install(download = { tar(source, ROOT) }, unpack = { archive, target -> unpackMigrationArchive(archive, target, indicator) })
    }
    assertEquals(emptyList<String>(), names(parent))
  }

  @Test
  fun `real archive with an entry that goes out of the folder gives the install error and nothing stays`() {
    assumeTar()
    val outside = tempDir.newFolder("outside").toPath()
    val links = listOf(outside.resolve("file").toString(), "../../../outside/file")
    for (link in links) {
      NioFiles.deleteRecursively(source)
      val root = source.resolve(ROOT)
      writeApp(root)
      root.resolve("link").createSymbolicLinkPointingTo(Path.of(link))
      val e = assertThrows(link, MigrationInstallException::class.java) { install(download = { tar(source, ROOT) }, unpack = ::unpack) }
      assertEquals(link, IdeBundle.message("updates.migration.error.install"), e.userMessage)
      assertEquals(link, emptyList<String>(), names(parent))
    }

    NioFiles.deleteRecursively(source)
    writeApp(source.resolve(ROOT))
    source.resolve("evil.txt").writeText("out of the root")
    val e = assertThrows(MigrationInstallException::class.java) {
      install(download = { tar(source.resolve(ROOT), ".", "../evil.txt") }, unpack = ::unpack)
    }
    assertEquals(IdeBundle.message("updates.migration.error.install"), e.userMessage)
    assertEquals(emptyList<String>(), names(parent))
    assertEquals(listOf("JetBrains"), names(parent.parent))
  }

  private fun install(
    download: () -> Path = { archive },
    unpack: (Path, Path) -> Unit = { _, target -> copySource(target) },
  ): Path = installLinuxApp(parent, "JB", EmptyProgressIndicator(ModalityState.nonModal()), download, unpack)

  private fun unpack(archive: Path, target: Path) {
    unpackMigrationArchive(archive, target, EmptyProgressIndicator(ModalityState.nonModal()))
  }

  private fun copySource(target: Path) {
    NioFiles.copyRecursively(source, target)
  }

  private fun writeApp(app: Path, product: String = "JB") {
    writeRootLauncher(app, osName = "Linux", launcherPath = LAUNCHER, productCode = product)
  }

  private fun names(dir: Path): List<String> = dir.listDirectoryEntries().map { it.name }.sorted()

  private fun assumeTar() {
    IoTestUtil.assumeUnix()
    assumeTrue("no tar", Files.isExecutable(Path.of("/usr/bin/tar")) || Files.isExecutable(Path.of("/bin/tar")))
  }

  /** Makes a `.tar.gz` of [members] of [dir] with the `tar` program. `-P` keeps a member name with `..`. */
  private fun tar(dir: Path, vararg members: String): Path {
    val file = tempDir.root.toPath().resolve("real.tar.gz")
    Files.deleteIfExists(file)
    val process = ProcessBuilder(listOf("tar", "-czPf", file.toString(), "-C", dir.toString()) + members)
      .redirectErrorStream(true)
      // without it, the `tar` program of macOS adds a `._<name>` entry for a file with an extended attribute
      .apply { environment()["COPYFILE_DISABLE"] = "1" }
      .start()
    val output = process.inputStream.bufferedReader().readText()
    assertEquals(output, 0, process.waitFor())
    return file
  }
}
