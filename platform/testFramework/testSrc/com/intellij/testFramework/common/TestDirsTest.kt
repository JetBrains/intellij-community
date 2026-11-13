// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.openapi.util.io.NioFiles
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

class TestDirsTest {
  @Test
  fun `deletes tree with read-only dir and file`(@TempDir tempDir: Path) {
    val root = tempDir.resolve("root")
    val nested = root.resolve("a").resolve("b").createDirectories()
    val file = nested.resolve("file.txt").apply { writeText("x") }
    NioFiles.setReadOnly(file, true)
    NioFiles.setReadOnly(nested, true)

    deleteTestDir(root)

    assertFalse(root.exists(), "$root must be deleted")
  }

  @Test
  @DisabledOnOs(OS.WINDOWS, disabledReason = "symlinks need a privilege on Windows; the junction test covers it")
  fun `keeps read-only target of symlink outside dir`(@TempDir tempDir: Path) {
    assertLinkTargetKept(tempDir) { link, target -> Files.createSymbolicLink(link, target) }
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  fun `keeps read-only target of junction outside dir`(@TempDir tempDir: Path) {
    assertLinkTargetKept(tempDir) { link, target ->
      val mklink = ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString()).inheritIO().start()
      assumeTrue(mklink.waitFor(30, TimeUnit.SECONDS) && mklink.exitValue() == 0, "can't create junction $link")
    }
  }

  private fun assertLinkTargetKept(tempDir: Path, createLink: (link: Path, target: Path) -> Unit) {
    val target = tempDir.resolve("outside").createDirectories()
    val targetFile = target.resolve("file.txt").apply { writeText("x") }
    NioFiles.setReadOnly(targetFile, true)
    NioFiles.setReadOnly(target, true)
    val root = tempDir.resolve("root").createDirectories()
    createLink(root.resolve("link"), target)
    try {
      deleteTestDir(root)

      assertFalse(root.exists(), "$root must be deleted")
      assertTrue(targetFile.exists(), "$targetFile outside the dir must survive")
      assertTrue(isReadOnly(targetFile), "$targetFile outside the dir must stay read-only")
    }
    finally {
      NioFiles.setReadOnly(target, false)
      NioFiles.setReadOnly(targetFile, false)
    }
  }

  @Test
  fun `missing dir is no-op`(@TempDir tempDir: Path) {
    val missing = tempDir.resolve("missing")

    deleteTestDir(missing)

    assertFalse(missing.exists())
  }

  @Test
  @DisabledOnOs(OS.WINDOWS, disabledReason = "copies the POSIX /bin/sleep")
  fun `stops process run from dir, then deletes dir`(@TempDir tempDir: Path) {
    val dir = tempDir.resolve("bin").createDirectories()
    val process = startSleepFrom(dir)
    try {
      deleteTestDir(dir)

      assertFalse(process.isAlive, "process ${process.pid()} must be stopped")
      assertFalse(dir.exists(), "$dir must be deleted")
    }
    finally {
      process.destroyForcibly()
    }
  }

  @Test
  @DisabledOnOs(OS.WINDOWS, disabledReason = "copies the POSIX /bin/sleep")
  fun `interrupted wait still stops process and deletes dir`(@TempDir tempDir: Path) {
    val dir = tempDir.resolve("bin").createDirectories()
    val process = startSleepFrom(dir)
    try {
      Thread.currentThread().interrupt()
      deleteTestDir(dir)

      assertTrue(Thread.interrupted(), "the interrupt must stay set")
      assertTrue(process.waitFor(10, TimeUnit.SECONDS), "process ${process.pid()} must be stopped")
      assertFalse(dir.exists(), "$dir must be deleted")
    }
    finally {
      Thread.interrupted()
      process.destroyForcibly()
    }
  }

  /** Starts a copy of `/bin/sleep` from [dir] and waits until the OS reports its executable. */
  private fun startSleepFrom(dir: Path): Process {
    val sleepBinary = Path.of("/bin/sleep")
    assumeTrue(Files.isExecutable(sleepBinary), "no $sleepBinary")
    val sleep = Files.copy(sleepBinary, dir.resolve("sleep"), StandardCopyOption.COPY_ATTRIBUTES)
    NioFiles.setExecutable(sleep)
    if (OS.current() == OS.MAC) {
      // macOS kills a copied system binary (SIGKILL, invalid code signature) unless it is re-signed
      val codesign = ProcessBuilder("/usr/bin/codesign", "--force", "--sign", "-", sleep.toString()).inheritIO().start()
      assumeTrue(codesign.waitFor(30, TimeUnit.SECONDS) && codesign.exitValue() == 0, "can't re-sign $sleep")
    }
    val process = ProcessBuilder(sleep.toString(), "600").start()
    // `deleteTestDir` finds the process by its executable, which the OS reports once the process has started
    val roots = listOf(dir, dir.toRealPath())
    val runsFromDir = { process.info().command().map { command -> roots.any { Path.of(command).startsWith(it) } }.orElse(false) }
    if (!awaitTrue(runsFromDir)) {
      process.destroyForcibly()
      throw AssertionError("process ${process.pid()} must be seen under $dir")
    }
    return process
  }

  @Test
  @DisabledOnOs(OS.WINDOWS, disabledReason = "a read-only parent dir doesn't block deletes on Windows")
  fun `never throws when delete fails`(@TempDir tempDir: Path) {
    val parent = tempDir.resolve("parent").createDirectories()
    val dir = parent.resolve("dir").createDirectories()
    dir.resolve("file.txt").writeText("x")
    NioFiles.setReadOnly(parent, true)
    try {
      assumeFalse(Files.isWritable(parent), "running as root: permissions are not enforced")

      deleteTestDir(dir)

      assertTrue(dir.exists(), "$dir can't be deleted from a read-only parent")
    }
    finally {
      NioFiles.setReadOnly(parent, false)
    }
  }

  /** Reads the permission bits, not `Files.isWritable`, which is true for root whatever the bits are. */
  private fun isReadOnly(path: Path): Boolean {
    val posix = Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
    if (posix != null) return PosixFilePermission.OWNER_WRITE !in posix.readAttributes().permissions()
    return Files.getFileAttributeView(path, DosFileAttributeView::class.java).readAttributes().isReadOnly
  }

  private fun awaitTrue(condition: () -> Boolean): Boolean {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
    while (System.nanoTime() < deadline) {
      if (condition()) return true
      Thread.sleep(50)
    }
    return condition()
  }
}
