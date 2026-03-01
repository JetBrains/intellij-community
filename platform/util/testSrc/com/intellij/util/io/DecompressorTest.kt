// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.IoTestUtil.assumeSymLinkCreationIsSupported
import com.intellij.util.SystemProperties
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.UnixStat
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Condition
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.Date
import java.util.function.Predicate
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.isExecutable
import kotlin.io.path.isHidden
import kotlin.io.path.isWritable
import kotlin.io.path.outputStream
import kotlin.io.path.readSymbolicLink
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class DecompressorTest {
  @Test fun noInternalTraversalInZip(@TempDir tempDir: Path) {
    val zip = tempDir.resolve("test.zip")
    ZipOutputStream(zip.outputStream()).use { writeEntry(it, "a/../bad.txt") }
    val dir = tempDir.resolve("unpacked")
    testNoTraversal(Decompressor.Zip(zip), dir, dir.resolve("bad.txt"))
    testNoTraversal(Decompressor.Zip(zip).withZipExtensions(), dir, dir.resolve("bad.txt"))
  }

  @Test fun noExternalTraversalInZip(@TempDir tempDir: Path) {
    val zip = tempDir.resolve("test.zip")
    ZipOutputStream(zip.outputStream()).use { writeEntry(it, "../evil.txt") }
    val dir = tempDir.resolve("unpacked")
    testNoTraversal(Decompressor.Zip(zip), dir, dir.parent.resolve("evil.txt"))
    testNoTraversal(Decompressor.Zip(zip).withZipExtensions(), dir, dir.parent.resolve("evil.txt"))
  }

  @Test fun noAbsolutePathsInZip(@TempDir tempDir: Path) {
    val zip = tempDir.resolve("test.zip")
    ZipOutputStream(zip.outputStream()).use { writeEntry(it, "/root.txt") }
    val dir1 = tempDir.resolve("unpacked1")
    Decompressor.Zip(zip).extract(dir1)
    assertThat(dir1.resolve("root.txt")).exists()
    val dir2 = tempDir.resolve("unpacked2")
    Decompressor.Zip(zip).withZipExtensions().extract(dir2)
    assertThat(dir2.resolve("root.txt")).exists()
  }

  @Test fun tarDetectionPlain(@TempDir tempDir: Path) {
    val tar = tempDir.resolve("test.tar")
    TarArchiveOutputStream(tar.outputStream()).use { writeEntry(it, "dir/file.txt") }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Tar(tar).extract(dir)
    assertThat(dir.resolve("dir/file.txt")).exists()
  }

  @Test fun tarDetectionGZip(@TempDir tempDir: Path) {
    val tar = tempDir.resolve("test.tgz")
    TarArchiveOutputStream(GzipCompressorOutputStream(tar.outputStream())).use { writeEntry(it, "dir/file.txt") }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Tar(tar).extract(dir)
    assertThat(dir.resolve("dir/file.txt")).exists()
  }

  @Test fun noInternalTraversalInTar(@TempDir tempDir: Path) {
    val tar = tempDir.resolve("test.tar")
    TarArchiveOutputStream(tar.outputStream()).use { writeEntry(it, "a/../bad.txt") }
    val dir = tempDir.resolve("unpacked")
    testNoTraversal(Decompressor.Tar(tar), dir, dir.resolve("bad.txt"))
  }

  @Test fun noExternalTraversalInTar(@TempDir tempDir: Path) {
    val tar = tempDir.resolve("test.tar")
    TarArchiveOutputStream(tar.outputStream()).use { writeEntry(it, "../evil.txt") }
    val dir = tempDir.resolve("unpacked")
    testNoTraversal(Decompressor.Tar(tar), dir, dir.parent.resolve("evil.txt"))
  }

  @Test fun noAbsolutePathsInTar(@TempDir tempDir: Path) {
    val tar = tempDir.resolve("test.tar")
    TarArchiveOutputStream(tar.outputStream()).use { writeEntry(it, "/root.txt") }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Tar(tar).extract(dir)
    assertThat(dir.resolve("root.txt")).exists()
  }

  @Test
  fun failsOnCorruptedZip(@TempDir tempDir: Path) {
    val zip = tempDir.createDirectories().resolve("test.zip").apply { writeText("whatever") }
    val dir = tempDir.resolve("unpacked")
    assertThatThrownBy {
      Decompressor.Zip(zip).extract(dir)
    }.isInstanceOf(ZipException::class.java)
  }

  @Test
  fun failsOnCorruptedExtZip(@TempDir tempDir: Path) {
    val zip = tempDir.resolve("test.zip").apply { writeText("whatever") }
    val dir = tempDir.resolve("unpacked")
    assertThatThrownBy {
      Decompressor.Zip(zip).withZipExtensions().extract(dir)
    }.hasRootCauseInstanceOf(ZipException::class.java)
  }

  @Test fun tarFileModes(@TempDir tempDir: Path) {
    val tar = tempDir.resolve("test.tar")
    TarArchiveOutputStream(tar.outputStream()).use {
      writeEntry(it, "dir/r", mode = 0b100_000_000)
      writeEntry(it, "dir/rw", mode = 0b110_000_000)
      writeEntry(it, "dir/rx", mode = 0b101_000_000)
      writeEntry(it, "dir/rwx", mode = 0b111_000_000)
    }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Tar(tar).extract(dir)
    if (SystemInfo.isWindows) {
      arrayOf("r", "rw", "rx", "rwx").forEach {
        assertThat(dir.resolve("dir/${it}")).exists().`is`(Writable)
      }
    }
    else {
      assertThat(dir.resolve("dir/r")).exists().isNot(Writable).isNot(Executable)
      assertThat(dir.resolve("dir/rw")).exists().`is`(Writable).isNot(Executable)
      assertThat(dir.resolve("dir/rx")).exists().isNot(Writable).`is`(Executable)
      assertThat(dir.resolve("dir/rwx")).exists().`is`(Writable).`is`(Executable)
    }
  }

  @Test fun zipUnixFileModes(@TempDir tempDir: Path) {
    val zip = tempDir.resolve("test.zip")
    ZipArchiveOutputStream(zip.outputStream()).use {
      writeEntry(it, "dir/r", mode = 0b100_000_000)
      writeEntry(it, "dir/rw", mode = 0b110_000_000)
      writeEntry(it, "dir/rx", mode = 0b101_000_000)
      writeEntry(it, "dir/rwx", mode = 0b111_000_000)
    }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Zip(zip).withZipExtensions().extract(dir)
    if (SystemInfo.isWindows) {
      arrayOf("r", "rw", "rx", "rwx").forEach {
        assertThat(dir.resolve("dir/${it}")).exists().`is`(Writable).isNot(Hidden)
      }
    }
    else {
      assertThat(dir.resolve("dir/r")).exists().isReadable().isNot(Writable).isNot(Executable)
      assertThat(dir.resolve("dir/rw")).exists().isReadable().`is`(Writable).isNot(Executable)
      assertThat(dir.resolve("dir/rx")).exists().isReadable().isNot(Writable).`is`(Executable)
      assertThat(dir.resolve("dir/rwx")).exists().isReadable().`is`(Writable).`is`(Executable)
    }
  }

  @Test fun zipDosFileModes(@TempDir tempDir: Path) {
    val zip = tempDir.resolve("test.zip")
    ZipArchiveOutputStream(zip.outputStream()).use {
      writeEntry(it, "dir/ro", readOnly = true)
      writeEntry(it, "dir/rw")
      writeEntry(it, "dir/h", hidden = true)
    }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Zip(zip).withZipExtensions().extract(dir)
    assertThat(dir.resolve("dir/ro")).exists().isNot(Writable)
    assertThat(dir.resolve("dir/rw")).exists().`is`(Writable)
    if (SystemInfo.isWindows) {
      assertThat(dir.resolve("dir/h")).exists().`is`(Hidden)
    }
  }

  @Test fun filtering(@TempDir tempDir: Path) {
    val zip = tempDir.resolve("test.zip")
    ZipOutputStream(zip.outputStream()).use {
      writeEntry(it, "d1/f1.txt")
      writeEntry(it, "d2/f2.txt")
    }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Zip(zip).filter(Predicate { !it.startsWith("d2/") }).extract(dir)
    assertThat(dir.resolve("d1/f1.txt")).isRegularFile()
    assertThat(dir.resolve("d2")).doesNotExist()
  }

  @Test fun tarSymlinks(@TempDir tempDir: Path) {
    assumeSymLinkCreationIsSupported()

    val rogueTarget = tempDir.resolve("rogue_f").apply { writeText("123789") }
    val tar = tempDir.resolve("test.tar")
    TarArchiveOutputStream(tar.outputStream()).use {
      writeEntry(it, "f")
      writeEntry(it, "links/ok", link = "../f")
      writeEntry(it, "rogue", link = "../rogue_f")
    }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Tar(tar).extract(dir)
    assertThat(dir.resolve("links/ok")).isSymbolicLink().hasSameBinaryContentAs(dir.resolve("f"))
    assertThat(dir.resolve("rogue")).isSymbolicLink().hasSameBinaryContentAs(rogueTarget)
  }

  @Test fun tarHardlinks(@TempDir tempDir: Path) {
    val tar = tempDir.resolve("test.tar")
    TarArchiveOutputStream(tar.outputStream()).use {
      writeEntry(it, "hardlink", link = "hardlink", type = TarArchiveEntry.LF_LINK)
    }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Tar(tar).extract(dir)

    assertThat(dir.resolve("hardlink")).doesNotExist()
  }

  @Test fun zipSymlinks(@TempDir tempDir: Path) {
    assumeSymLinkCreationIsSupported()

    val rogueTarget = tempDir.resolve("rogue_f").apply { writeText("123789") }
    val zip = tempDir.resolve("test.zip")
    ZipArchiveOutputStream(zip.outputStream()).use {
      writeEntry(it, "f")
      writeEntry(it, "links/ok", link = "../f")
      writeEntry(it, "rogue", link = "../rogue_f")
    }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Zip(zip).withZipExtensions().extract(dir)
    assertThat(dir.resolve("links/ok")).isSymbolicLink().hasSameBinaryContentAs(dir.resolve("f"))
    assertThat(dir.resolve("rogue")).isSymbolicLink().hasSameBinaryContentAs(rogueTarget)
  }

  @Test fun zipRogueSymlinks(@TempDir tempDir: Path) {
    assumeSymLinkCreationIsSupported()

    val zip = tempDir.resolve("test.zip")
    ZipArchiveOutputStream(zip.outputStream()).use { writeEntry(it, "rogue", link = "../f") }

    val decompressor = Decompressor.Zip(zip).withZipExtensions().escapingSymlinkPolicy(
      Decompressor.EscapingSymlinkPolicy.DISALLOW)
    val dir = tempDir.resolve("unpacked")
    testNoTraversal(decompressor, dir, dir.resolve("rogue"))
  }

  @Test fun tarRogueSymlinks(@TempDir tempDir: Path) {
    assumeSymLinkCreationIsSupported()

    val tar = tempDir.resolve("test.tar")
    TarArchiveOutputStream(tar.outputStream()).use { writeEntry(it, "rogue", link = "../f") }

    val decompressor = Decompressor.Tar(tar).escapingSymlinkPolicy(
      Decompressor.EscapingSymlinkPolicy.DISALLOW)
    val dir = tempDir.resolve("unpacked")
    testNoTraversal(decompressor, dir, dir.resolve("rogue"))
  }

  @Test fun prefixPathsFilesInZip(@TempDir tempDir: Path) {
    val zip = tempDir.resolve("test.zip")
    ZipOutputStream(zip.outputStream()).use {
      writeEntry(it, "a/b/c.txt")
    }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Zip(zip).removePrefixPath("a/b").extract(dir)

    assertThat(dir.resolve("c.txt")).isRegularFile()
    assertThat(dir.resolve("a")).doesNotExist()
    assertThat(dir.resolve("a/b")).doesNotExist()
    assertThat(dir.resolve("b")).doesNotExist()
  }

  @Test fun prefixPathsFilesInCommonsZip(@TempDir tempDir: Path) {
    val zip = tempDir.resolve("test.zip")
    ZipOutputStream(zip.outputStream()).use {
      writeEntry(it, "a/b/c.txt")
    }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Zip(zip).removePrefixPath("a/b").extract(dir)

    assertThat(dir.resolve("c.txt")).isRegularFile()
    assertThat(dir.resolve("a")).doesNotExist()
    assertThat(dir.resolve("a/b")).doesNotExist()
    assertThat(dir.resolve("b")).doesNotExist()
  }

  @Test fun prefixPathFilesInZipWithFilter(@TempDir tempDir: Path) {
    val zip = tempDir.resolve("test.zip")
    ZipOutputStream(zip.outputStream()).use {
      writeEntry(it, "a/b/c.txt")
      writeEntry(it, "skip.txt")
    }
    val dir = tempDir.resolve("unpacked")
    val filterLog = mutableListOf<String>()
    Decompressor.Zip(zip).removePrefixPath("a/b").filter(Predicate{  filterLog.add(it) }).extract(dir)

    assertThat(dir.resolve("c.txt")).isRegularFile()
    assertThat(dir.resolve("a")).doesNotExist()
    assertThat(dir.resolve("a/b")).doesNotExist()
    assertThat(dir.resolve("b")).doesNotExist()

    assertThat(filterLog).containsExactlyInAnyOrder("a/b/c.txt", "skip.txt")
  }

  @Test fun prefixPathsFilesInTarWithSymlinks(@TempDir tempDir: Path) {
    assumeSymLinkCreationIsSupported()

    val tar = tempDir.resolve("test.tar")
    TarArchiveOutputStream(tar.outputStream()).use {
      writeEntry(it, "a/f")
      writeEntry(it, "a/links/ok", link = "../f")
    }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Tar(tar).removePrefixPath("a").extract(dir)

    assertThat(dir.resolve("f")).isRegularFile()
    assertThat(dir.resolve("links/ok")).isSymbolicLink().hasSameBinaryContentAs(dir.resolve("f"))
  }

  @Test fun prefixPathFillMatch(@TempDir tempDir: Path) {
    val tar = tempDir.resolve("test.tar")
    TarArchiveOutputStream(tar.outputStream()).use {
      writeEntry(it, "./a/f")
    }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Tar(tar).removePrefixPath("/a/f").extract(dir)

    assertThat(dir.resolve("f")).doesNotExist()
  }

  @Test fun prefixPathWithSlashTar(@TempDir tempDir: Path) {
    val tar = tempDir.resolve("test.tar")
    TarArchiveOutputStream(tar.outputStream()).use {
      writeEntry(it, "./a/f")
      writeEntry(it, "/a/g")
      writeEntry(it, "././././././//a/h")
    }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Tar(tar).removePrefixPath("/a/").extract(dir)

    assertThat(dir.resolve("f")).isRegularFile()
    assertThat(dir.resolve("g")).isRegularFile()
    assertThat(dir.resolve("h")).isRegularFile()
  }

  @Test fun prefixPathWithDotTar(@TempDir tempDir: Path) {
    val tar = tempDir.resolve("test.tar")
    TarArchiveOutputStream(tar.outputStream()).use {
      writeEntry(it, "./a/b/f")
      writeEntry(it, "/a/b/g")
      writeEntry(it, "././././././//a/b/h")
    }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Tar(tar).removePrefixPath("./a/b").extract(dir)

    assertThat(dir.resolve("f")).isRegularFile()
    assertThat(dir.resolve("g")).isRegularFile()
    assertThat(dir.resolve("h")).isRegularFile()
  }

  @Test fun prefixPathWithCommonsZip(@TempDir tempDir: Path) {
    val zip = tempDir.resolve("test.zip")
    ZipArchiveOutputStream(zip.outputStream()).use {
      writeEntry(it, "./a/b/f")
      writeEntry(it, "/a/b/g")
      writeEntry(it, "././././././//a/b/h")
    }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Zip(zip).removePrefixPath("./a/b").extract(dir)

    assertThat(dir.resolve("f")).isRegularFile()
    assertThat(dir.resolve("g")).isRegularFile()
    assertThat(dir.resolve("h")).isRegularFile()
  }

  @Test fun prefixPathTarSymlink(@TempDir tempDir: Path) {
    assumeSymLinkCreationIsSupported()

    val tar = tempDir.resolve("test.tar")
    TarArchiveOutputStream(tar.outputStream()).use {
      writeEntry(it, "./a/b/f")
      writeEntry(it, "a/b/links/ok", link = "../f")
    }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Tar(tar).removePrefixPath("a/b").extract(dir)

    assertThat(dir.resolve("f")).isRegularFile()
    assertThat(dir.resolve("links/ok")).isSymbolicLink().hasSameBinaryContentAs(dir.resolve("f"))
  }

  @Test fun prefixPathZipSymlink(@TempDir tempDir: Path) {
    assumeSymLinkCreationIsSupported()

    val zip = tempDir.resolve("test.zip")
    ZipArchiveOutputStream(zip.outputStream()).use {
      writeEntry(it, "./a/b/f")
      writeEntry(it, "a/b/links/ok", link = "../f")
    }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Zip(zip).withZipExtensions().removePrefixPath("a/b").extract(dir)

    assertThat(dir.resolve("f")).isRegularFile()
    assertThat(dir.resolve("links/ok")).isSymbolicLink().hasSameBinaryContentAs(dir.resolve("f"))
  }

  @Test fun prefixPathTarRogueSymlinks(@TempDir tempDir: Path) {
    assumeSymLinkCreationIsSupported()

    val tar = tempDir.resolve("test.tar")
    TarArchiveOutputStream(tar.outputStream()).use { writeEntry(it, "a/b/c/rogue", link = "../f") }

    val decompressor = Decompressor.Tar(tar).escapingSymlinkPolicy(
      Decompressor.EscapingSymlinkPolicy.DISALLOW).removePrefixPath("a/b/c")
    val dir = tempDir.resolve("unpacked")
    testNoTraversal(decompressor, dir, dir.resolve("rogue"))
  }

  @Test fun prefixPathZipRogueSymlinks(@TempDir tempDir: Path) {
    assumeSymLinkCreationIsSupported()

    val zip = tempDir.resolve("test.zip")
    ZipArchiveOutputStream(zip.outputStream()).use { writeEntry(it, "a/b/c/rogue", link = "../f") }

    val decompressor = Decompressor.Zip(zip).withZipExtensions().escapingSymlinkPolicy(
      Decompressor.EscapingSymlinkPolicy.DISALLOW).removePrefixPath("a/b/c")
    val dir = tempDir.resolve("unpacked")
    testNoTraversal(decompressor, dir, dir.resolve("rogue"))
  }

  @Test fun prefixPathSkipsTooShortPaths(@TempDir tempDir: Path) {
    val tar = tempDir.resolve("test.tar")
    TarArchiveOutputStream(tar.outputStream()).use {
      writeEntry(it, "missed")
      writeEntry(it, "a/b/c/file.txt")
    }
    val dir = tempDir.resolve("unpacked")
    Decompressor.Tar(tar).removePrefixPath("a/b").extract(dir)
    assertThat(dir.resolve("c/file.txt")).isRegularFile()
    assertThat(dir.resolve("missed")).doesNotExist()
  }

  @Test fun fileOverwrite(@TempDir tempDir: Path) {
    val zip = tempDir.resolve("test.zip")
    ZipOutputStream(zip.outputStream()).use { writeEntry(it, "a/file.txt") }
    val dir = tempDir.resolve("unpacked")
    val file = tempDir.resolve("unpacked/a/file.txt").createParentDirectories().apply { writeBytes(byteArrayOf(0)) }
    Decompressor.Zip(zip).extract(dir)
    assertThat(file).hasBinaryContent(TestContent)
  }

  @Test fun symlinkOverwrite(@TempDir tempDir: Path) {
    assumeSymLinkCreationIsSupported()

    val tar = tempDir.resolve("test.tar")
    TarArchiveOutputStream(tar.outputStream()).use {
      writeEntry(it, "a/file")
      writeEntry(it, "a/link", link = "file")
    }
    val dir = tempDir.resolve("unpacked")
    val target = tempDir.resolve("unpacked/a/target").createParentDirectories().apply { writeBytes(byteArrayOf(0)) }
    val link = tempDir.resolve("unpacked/a/link").createSymbolicLinkPointingTo(target)
    Decompressor.Tar(tar).extract(dir)
    assertThat(link).isSymbolicLink().hasBinaryContent(TestContent)
  }

  @Test fun extZipPureNIO() {
    MemoryFileSystemBuilder.newLinux().build("${DecompressorTest::class.simpleName}.extZipPureNIO").use { fs ->
      val testDir = fs.getPath("/home/${SystemProperties.getUserName()}")
      val zip = testDir.resolve("test.zip").createFile()
      ZipArchiveOutputStream(zip.outputStream()).use {
        writeEntry(it, "dir/r", mode = 0b100_000_000)
      }
      val dir = testDir.resolve("unpacked").createDirectories()
      Decompressor.Zip(zip).withZipExtensions().extract(dir)
      assertThat(dir.resolve("dir/r")).exists()
    }
  }

  @Test fun tarPureNIO() {
    MemoryFileSystemBuilder.newLinux().build("${DecompressorTest::class.simpleName}.tarPureNIO").use { fs ->
      val testDir = fs.getPath("/home/${SystemProperties.getUserName()}")
      val tar = testDir.resolve("test.tar").createFile()
      TarArchiveOutputStream(tar.outputStream()).use {
        writeEntry(it, "dir/r", mode = 0b100_000_000)
      }
      val dir = testDir.resolve("unpacked").createDirectories()
      Decompressor.Tar(tar).extract(dir)
      assertThat(dir.resolve("dir/r")).exists()
    }
  }

  @Test fun absoluteSymlinkToRelativeWithOptionSet(@TempDir tempDir: Path) {
    val tar = tempDir.resolve("test.tar")
    TarArchiveOutputStream(tar.outputStream()).use {
      writeEntry(it, "symlink", link = "/root")
    }

    val dir = tempDir.resolve("unpacked")
    Decompressor.Tar(tar).escapingSymlinkPolicy(
      Decompressor.EscapingSymlinkPolicy.RELATIVIZE_ABSOLUTE).extract(dir)

    val symlink = dir.resolve("symlink")
    assertThat(symlink).isSymbolicLink()
    assertThat(symlink.readSymbolicLink()).isEqualTo(dir.resolve("root"))
  }

  @Test fun retryStrategyErrorHandler(@TempDir tempDir: Path) {
    val zip = tempDir.resolve("retryStrategyErrorHandler.zip")
    ZipArchiveOutputStream(zip.outputStream()).use {
      writeEntry(it, "good-file.txt")
      writeEntry(it, "bad-file.txt", link = "")
      writeEntry(it, "good-file-too.md")
    }
    var retries = 3 // to make sure if we really make retrying
    val dir = tempDir.resolve("unpacked")

    Decompressor.Zip(zip).withZipExtensions().errorHandler { _, _ ->
      if (retries == 0) {
        return@errorHandler Decompressor.ErrorHandlerChoice.SKIP
      }
      retries--
      Decompressor.ErrorHandlerChoice.RETRY
    }.extract(dir)

    assertThat(retries).isEqualTo(0)
    assertThat(dir.resolve("good-file.txt")).exists()
    assertThat(dir.resolve("bad-file.txt")).doesNotExist()
    assertThat(dir.resolve("good-file-too.md")).exists()
  }

  @Test fun abortStrategyErrorHandlerDeletingFiles(@TempDir tempDir: Path) {
    val zip = tempDir.resolve("abortStrategyErrorHandlerDeletingFiles.zip")
    ZipArchiveOutputStream(zip.outputStream()).use {
      writeEntry(it, "good-file.txt")
      writeEntry(it, "very-good-file.java")
      writeEntry(it, "bad-file.txt", link = "")
    }

    val dir = tempDir.resolve("unpacked")
    Decompressor.Zip(zip).withZipExtensions().errorHandler { _, _ -> Decompressor.ErrorHandlerChoice.ABORT }.extract(dir)

    assertThat(dir.resolve("good-file.txt")).doesNotExist()
    assertThat(dir.resolve("very-good-file.java")).doesNotExist()
  }

  @Test fun skipStrategyErrorHandlerNotDeletingFiles(@TempDir tempDir: Path) {
    val zip = tempDir.resolve("skipStrategyErrorHandlerNotDeletingFiles.zip")
    ZipArchiveOutputStream(zip.outputStream()).use {
      writeEntry(it, "good-file.txt")
      writeEntry(it, "bad-file.txt", link = "")
      writeEntry(it, "very-good-file.java")
    }

    val dir = tempDir.resolve("unpacked")
    Decompressor.Zip(zip).withZipExtensions().errorHandler { _, _ -> Decompressor.ErrorHandlerChoice.SKIP }.extract(dir)

    assertThat(dir.resolve("good-file.txt")).exists()
    assertThat(dir.resolve("very-good-file.java")).exists()
  }

  @Test fun doNothingStrategyErrorHandlerNotDeletingFilesBeforeSkipAfter(@TempDir tempDir: Path) {
    val zip = tempDir.resolve("doNothingStrategyErrorHandlerNotDeletingFilesBeforeSkipAfter.zip")
    ZipArchiveOutputStream(zip.outputStream()).use {
      writeEntry(it, "good-file.txt")
      writeEntry(it, "very-good-file.java")
      writeEntry(it, "bad-file.txt", link = "")
      writeEntry(it, "good-but-unlucky-file.kt")
    }

    val dir = tempDir.resolve("unpacked")
    assertThatThrownBy {
      Decompressor.Zip(zip).withZipExtensions().errorHandler { _, _ -> Decompressor.ErrorHandlerChoice.BAIL_OUT }.extract(dir)
    }.isInstanceOf(IOException::class.java)

    assertThat(dir.resolve("good-file.txt")).exists()
    assertThat(dir.resolve("very-good-file.java")).exists()
    assertThat(dir.resolve("good-but-unlucky-file.kt")).doesNotExist()
  }

  @Test fun skipAllStrategyCalledOnlyOnce(@TempDir tempDir: Path) {
    val zip = tempDir.resolve("skipAllStrategyCalledOnlyOnce.zip")
    ZipArchiveOutputStream(zip.outputStream()).use {
      writeEntry(it, "bad-file.txt", link = "")
      writeEntry(it, "another-bad-file.txt", link = "")
      writeEntry(it, "another-bad-file.cpp", link = "")
    }
    var isCalled = false
    val dir = tempDir.resolve("unpacked")

    Decompressor.Zip(zip).withZipExtensions().errorHandler { _, _ ->
      if (!isCalled) {
        isCalled = true
        Decompressor.ErrorHandlerChoice.SKIP_ALL
      } else {
        throw AssertionError("SKIP_ALL strategy can't be called twice")
      }
    }.extract(dir)

    assertThat(isCalled).isTrue
  }

  //<editor-fold desc="Helpers.">
  private fun writeEntry(zip: ZipOutputStream, name: String) {
    val entry = ZipEntry(name)
    entry.time = System.currentTimeMillis()
    zip.putNextEntry(entry)
    zip.write(TestContent)
    zip.closeEntry()
  }

  private fun writeEntry(tar: TarArchiveOutputStream, name: String, mode: Int = 0, link: String? = null, type: Byte = TarArchiveEntry.LF_SYMLINK) {
    if (link != null) {
      val entry = TarArchiveEntry(name, type)
      entry.modTime = Date()
      entry.linkName = link
      entry.size = 0
      tar.putArchiveEntry(entry)
    }
    else {
      val entry = TarArchiveEntry(name)
      entry.modTime = Date()
      entry.size = TestContent.size.toLong()
      if (mode != 0) entry.mode = mode
      tar.putArchiveEntry(entry)
      tar.write(TestContent)
    }
    tar.closeArchiveEntry()
  }

  private fun writeEntry(zip: ZipArchiveOutputStream, name: String, mode: Int = 0, readOnly: Boolean = false, hidden: Boolean = false, link: String? = null) {
    val entry = ZipArchiveEntry(name)
    entry.lastModifiedTime = FileTime.from(Instant.now())
    if (link != null) {
      entry.unixMode = UnixStat.LINK_FLAG
      zip.putArchiveEntry(entry)
      zip.write(link.toByteArray())
    }
    else {
      when {
        mode != 0 -> entry.unixMode = mode
        readOnly || hidden -> {
          var dosAttributes = entry.externalAttributes
          if (readOnly) dosAttributes = dosAttributes or 0b01
          if (hidden) dosAttributes = dosAttributes or 0b10
          entry.externalAttributes = dosAttributes
        }
      }
      zip.putArchiveEntry(entry)
      zip.write(TestContent)
    }
    zip.closeArchiveEntry()
  }

  private fun testNoTraversal(decompressor: Decompressor, dir: Path, unexpected: Path) {
    val error = runCatching { decompressor.extract(dir) }.exceptionOrNull()
    assertThat(unexpected).doesNotExist()
    assertThat(error?.message).contains(unexpected.fileName.toString())
  }

  companion object {
    private val TestContent = "...".toByteArray()
    private val Writable = Condition<Path>(Predicate { it.isWritable() }, "writable")
    private val Executable = Condition<Path>(Predicate { it.isExecutable() }, "executable")
    private val Hidden = Condition<Path>(Predicate { it.isHidden() }, "hidden")
  }
  //</editor-fold>
}
