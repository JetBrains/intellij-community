// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.productMigration

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.EmptyProgressIndicatorBase
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.util.io.sha256Hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

private const val HEX = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

class MigrationInstallerTest {
  @get:Rule
  val tempDir = TemporaryFolder()

  @Test
  fun `checksum file formats are accepted`() {
    assertEquals(HEX, parseSha256File(HEX))
    assertEquals(HEX, parseSha256File("${HEX}  unified-ide-271.2227-aarch64.dmg"))
    assertEquals(HEX, parseSha256File("${HEX} *aqua-2024.3.2-aarch64.dmg\n"))
    assertEquals(HEX, parseSha256File("\n  ${HEX.uppercase(Locale.ROOT)}\n"))
  }

  @Test
  fun `bad checksum files are rejected`() {
    assertNull(parseSha256File(""))
    assertNull(parseSha256File("  \n"))
    assertNull(parseSha256File(HEX.dropLast(1)))
    assertNull(parseSha256File("${HEX}0"))
    assertNull(parseSha256File(HEX.replaceFirst('0', 'g')))
    assertNull(parseSha256File("<html>Not Found</html>"))
  }

  @Test
  fun `single application is found in the mounted image`() {
    IoTestUtil.assumeSymLinkCreationIsSupported()
    val mount = tempDir.newFolder("mount").toPath()
    val app = mount.resolve("Aqua.app").createDirectory()
    mount.resolve("Applications").createSymbolicLinkPointingTo(Path.of("/Applications"))
    mount.resolve(".background").createDirectory()
    mount.resolve("Link.app").createSymbolicLinkPointingTo(app)
    assertEquals(app, findSingleApp(mount))
  }

  @Test
  fun `no application or two applications give no result`() {
    val mount = tempDir.newFolder("mount").toPath()
    assertNull(findSingleApp(mount))
    mount.resolve("A.app").createDirectory()
    mount.resolve("B.app").createDirectory()
    assertNull(findSingleApp(mount))
  }

  @Test
  fun `verified download returns the file`() {
    val source = source()
    Files.writeString(checksumOf(source), "${sha256Hex(source).uppercase(Locale.ROOT)} *${source.fileName}")
    val workDir = workDir()
    val result = downloadAndVerify(url(source), workDir, indicator(), allowFileUrls = true)
    assertEquals(workDir, result.parent)
    assertArrayEquals(source.readBytes(), result.readBytes())
  }

  @Test
  fun `missing checksum stops before the download`() {
    val workDir = workDir()
    assertChecksumError { downloadAndVerify(url(source()), workDir, indicator(), allowFileUrls = true) }
    assertEquals(emptyList<Path>(), workDir.listDirectoryEntries())
  }

  @Test
  fun `bad checksum format stops before the download`() {
    val source = source()
    checksumOf(source).writeText("not a checksum")
    val workDir = workDir()
    assertChecksumError { downloadAndVerify(url(source), workDir, indicator(), allowFileUrls = true) }
    assertEquals(emptyList<Path>(), workDir.listDirectoryEntries())
  }

  @Test
  fun `checksum mismatch leaves no file`() {
    val source = source()
    checksumOf(source).writeText(HEX)
    val workDir = tempDir.root.toPath().resolve("work")
    assertChecksumError { withMigrationWorkDir(workDir) { downloadAndVerify(url(source), it, indicator(), allowFileUrls = true) } }
    assertFalse(workDir.exists())
  }

  @Test
  fun `url that is not allowed downloads nothing`() {
    val source = source()
    Files.writeString(checksumOf(source), sha256Hex(source))
    val workDir = workDir()
    val error = assertThrows(MigrationInstallException::class.java) {
      downloadAndVerify(url(source), workDir, indicator(), allowFileUrls = false)
    }
    assertEquals(IdeBundle.message("updates.migration.content.invalid"), error.userMessage)
    assertEquals(emptyList<Path>(), workDir.listDirectoryEntries())
  }

  @Test
  fun `work dir is deleted after a success, a failure and a cancel`() {
    val workDir = tempDir.root.toPath().resolve("work")
    workDir.createDirectories().resolve("stale.dmg").writeText("stale")

    assertEquals("done", withMigrationWorkDir(workDir) {
      assertEquals(emptyList<Path>(), it.listDirectoryEntries())
      it.resolve("distribution.dmg").writeText("x")
      "done"
    })
    assertFalse(workDir.exists())

    assertThrows(IllegalStateException::class.java) {
      withMigrationWorkDir(workDir) {
        it.resolve("distribution.dmg").writeText("x")
        error("failure")
      }
    }
    assertFalse(workDir.exists())

    assertThrows(ProcessCanceledException::class.java) {
      withMigrationWorkDir(workDir) {
        it.resolve("distribution.dmg").writeText("x")
        throw ProcessCanceledException()
      }
    }
    assertFalse(workDir.exists())
  }

  @Test
  fun `cancel of the download leaves no work dir`() {
    val source = source()
    Files.writeString(checksumOf(source), sha256Hex(source))
    val workDir = tempDir.root.toPath().resolve("work")
    val downloadText = IdeBundle.message("updates.migration.progress.download")
    val indicator = object : EmptyProgressIndicatorBase(ModalityState.nonModal()) {
      private var canceled = false
      override fun setText(text: String?) {
        if (text == downloadText) canceled = true
      }
      override fun cancel() {
        canceled = true
      }
      override fun isCanceled(): Boolean = canceled
    }
    assertThrows(ProcessCanceledException::class.java) {
      withMigrationWorkDir(workDir) { downloadAndVerify(url(source), it, indicator, allowFileUrls = true) }
    }
    assertFalse(workDir.exists())
  }

  private fun source(): Path = tempDir.root.toPath().resolve("unified-ide-271.2227-aarch64.dmg").apply { writeBytes(ByteArray(100_000) { it.toByte() }) }

  private fun indicator(): EmptyProgressIndicator = EmptyProgressIndicator(ModalityState.nonModal())

  private fun checksumOf(source: Path): Path = source.resolveSibling("${source.fileName}.sha256")

  private fun workDir(): Path = tempDir.newFolder("work").toPath()

  private fun url(source: Path): String = source.toUri().toString()

  private fun assertChecksumError(action: () -> Unit) {
    val error = assertThrows(MigrationInstallException::class.java) { action() }
    assertEquals(IdeBundle.message("updates.migration.error.checksum"), error.userMessage)
  }
}
