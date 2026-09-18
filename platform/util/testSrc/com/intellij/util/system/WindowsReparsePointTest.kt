// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import com.intellij.openapi.util.io.IoTestUtil
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.InstanceOfAssertFactories.type
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS.WINDOWS
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.div
import kotlin.io.path.pathString
import kotlin.io.path.writeText

/**
 * Only the AppX subsystem writes an `IO_REPARSE_TAG_APPEXECLINK`, so no test can make one. A junction carries
 * `IO_REPARSE_TAG_MOUNT_POINT`, it needs no administrator, and it exercises the same native path.
 */
@EnabledOnOs(WINDOWS)
internal class WindowsReparsePointTest {
  @Test
  fun `reads the tag of a junction`(@TempDir directory: Path) {
    val link = junction(directory)

    val point = WindowsReparsePoint.read(link).getOrThrow()

    assertThat(point.tag).isEqualTo(WindowsReparsePoint.IO_REPARSE_TAG_MOUNT_POINT)
  }

  @Test
  fun `reads the target of a junction`(@TempDir directory: Path) {
    val target = (directory / "target").createDirectory()
    val link = junction(directory, target)

    val point = WindowsReparsePoint.read(link).getOrThrow()

    // A MountPointReparseBuffer holds four USHORT fields, then the names. The substitute name holds the target.
    val text = String(point.data, StandardCharsets.UTF_16LE)
    assertThat(text).contains(target.pathString)
  }

  @Test
  fun `reads the tag of a symbolic link`(@TempDir directory: Path) {
    IoTestUtil.assumeSymLinkCreationIsSupported()
    val target = (directory / "target.txt").also { it.writeText("text") }
    val link = directory / "link.txt"
    IoTestUtil.createSymLink(target.pathString, link.pathString)

    val point = WindowsReparsePoint.read(link).getOrThrow()

    assertThat(point.tag).isEqualTo(WindowsReparsePoint.IO_REPARSE_TAG_SYMLINK)
  }

  @Test
  fun `reports a file that carries no reparse point`(@TempDir directory: Path) {
    val file = (directory / "plain.txt").also { it.writeText("text") }

    val failure = WindowsReparsePoint.read(file).exceptionOrNull()

    assertThat(failure)
      .asInstanceOf(type(WindowsException::class.java))
      .extracting { it.errorCode }
      .isEqualTo(WindowsReparsePoint.ERROR_NOT_A_REPARSE_POINT)
  }

  /** @return a junction in [directory] that points to [target], a directory that the caller owns */
  private fun junction(directory: Path, target: Path = (directory / "target").createDirectory()): Path {
    val link = directory / "link"
    IoTestUtil.createJunction(target.pathString, link.pathString)
    return link
  }
}
