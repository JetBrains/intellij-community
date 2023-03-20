// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.IoTestUtil.assumeSymLinkCreationIsSupported
import com.intellij.testFramework.rules.TempDirectory
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
import org.junit.Rule
import org.junit.Test
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.*
import java.util.function.Predicate
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

class DecompressorTest {
  @Rule @JvmField var tempDir = TempDirectory()

  @Test fun noInternalTraversalInZip() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use { writeEntry(it, "a/../bad.txt") }
    val dir = tempDir.newDirectory("unpacked").toPath()
    testNoTraversal(Decompressor.Zip(zip), dir, dir.resolve("bad.txt"))
    testNoTraversal(Decompressor.Zip(zip).withZipExtensions(), dir, dir.resolve("bad.txt"))
  }

  @Test fun noExternalTraversalInZip() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use { writeEntry(it, "../evil.txt") }
    val dir = tempDir.newDirectory("unpacked").toPath()
    testNoTraversal(Decompressor.Zip(zip), dir, dir.parent.resolve("evil.txt"))
    testNoTraversal(Decompressor.Zip(zip).withZipExtensions(), dir, dir.parent.resolve("evil.txt"))
  }

  @Test fun noAbsolutePathsInZip() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use { writeEntry(it, "/root.txt") }
    val dir1 = tempDir.newDirectory("unpacked1").toPath()
    Decompressor.Zip(zip).extract(dir1)
    assertThat(dir1.resolve("root.txt")).exists()
    val dir2 = tempDir.newDirectory("unpacked2").toPath()
    Decompressor.Zip(zip).withZipExtensions().extract(dir2)
    assertThat(dir2.resolve("root.txt")).exists()
  }

  @Test fun tarDetectionPlain() {
    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use { writeEntry(it, "dir/file.txt") }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Tar(tar).extract(dir)
    assertThat(dir.resolve("dir/file.txt")).exists()
  }

  @Test fun tarDetectionGZip() {
    val tar = tempDir.newFile("test.tgz")
    TarArchiveOutputStream(GzipCompressorOutputStream(FileOutputStream(tar))).use { writeEntry(it, "dir/file.txt") }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Tar(tar).extract(dir)
    assertThat(dir.resolve("dir/file.txt")).exists()
  }

  @Test fun noInternalTraversalInTar() {
    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use { writeEntry(it, "a/../bad.txt") }
    val dir = tempDir.newDirectory("unpacked").toPath()
    testNoTraversal(Decompressor.Tar(tar), dir, dir.resolve("bad.txt"))
  }

  @Test fun noExternalTraversalInTar() {
    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use { writeEntry(it, "../evil.txt") }
    val dir = tempDir.newDirectory("unpacked").toPath()
    testNoTraversal(Decompressor.Tar(tar), dir, dir.parent.resolve("evil.txt"))
  }

  @Test fun noAbsolutePathsInTar() {
    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use { writeEntry(it, "/root.txt") }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Tar(tar).extract(dir)
    assertThat(dir.resolve("root.txt")).exists()
  }

  @Test(expected = ZipException::class)
  fun failsOnCorruptedZip() {
    val zip = tempDir.newFile("test.zip")
    zip.writeText("whatever")
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Zip(zip).extract(dir)
  }

  @Test
  fun failsOnCorruptedExtZip() {
    val zip = tempDir.newFile("test.zip")
    zip.writeText("whatever")
    val dir = tempDir.newDirectory("unpacked").toPath()
    assertThatThrownBy {
      Decompressor.Zip(zip).withZipExtensions().extract(dir)
    }.hasRootCauseInstanceOf(ZipException::class.java)
  }

  @Test fun tarFileModes() {
    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use {
      writeEntry(it, "dir/r", mode = 0b100_000_000)
      writeEntry(it, "dir/rw", mode = 0b110_000_000)
      writeEntry(it, "dir/rx", mode = 0b101_000_000)
      writeEntry(it, "dir/rwx", mode = 0b111_000_000)
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
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

  @Test fun zipUnixFileModes() {
    val zip = tempDir.newFile("test.zip")
    ZipArchiveOutputStream(FileOutputStream(zip)).use {
      writeEntry(it, "dir/r", mode = 0b100_000_000)
      writeEntry(it, "dir/rw", mode = 0b110_000_000)
      writeEntry(it, "dir/rx", mode = 0b101_000_000)
      writeEntry(it, "dir/rwx", mode = 0b111_000_000)
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
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

  @Test fun zipDosFileModes() {
    val zip = tempDir.newFile("test.zip")
    ZipArchiveOutputStream(FileOutputStream(zip)).use {
      writeEntry(it, "dir/ro", readOnly = true)
      writeEntry(it, "dir/rw")
      writeEntry(it, "dir/h", hidden = true)
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Zip(zip).withZipExtensions().extract(dir)
    assertThat(dir.resolve("dir/ro")).exists().isNot(Writable)
    assertThat(dir.resolve("dir/rw")).exists().`is`(Writable)
    if (SystemInfo.isWindows) {
      assertThat(dir.resolve("dir/h")).exists().`is`(Hidden)
    }
  }

  @Test fun filtering() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use {
      writeEntry(it, "d1/f1.txt")
      writeEntry(it, "d2/f2.txt")
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Zip(zip).filter(Predicate { !it.startsWith("d2/") }).extract(dir)
    assertThat(dir.resolve("d1/f1.txt")).isRegularFile()
    assertThat(dir.resolve("d2")).doesNotExist()
  }

  @Test fun tarSymlinks() {
    assumeSymLinkCreationIsSupported()

    val rogueTarget = tempDir.newFile("rogue_f", "123789".toByteArray(Charsets.UTF_8))
    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use {
      writeEntry(it, "f")
      writeEntry(it, "links/ok", link = "../f")
      writeEntry(it, "rogue", link = "../rogue_f")
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Tar(tar).extract(dir)
    assertThat(dir.resolve("links/ok")).isSymbolicLink().hasSameBinaryContentAs(dir.resolve("f"))
    assertThat(dir.resolve("rogue")).isSymbolicLink().hasSameBinaryContentAs(rogueTarget.toPath())
  }

  @Test fun zipSymlinks() {
    assumeSymLinkCreationIsSupported()

    val rogueTarget = tempDir.newFile("rogue_f", "123789".toByteArray(Charsets.UTF_8))
    val zip = tempDir.newFile("test.zip")
    ZipArchiveOutputStream(FileOutputStream(zip)).use {
      writeEntry(it, "f")
      writeEntry(it, "links/ok", link = "../f")
      writeEntry(it, "rogue", link = "../rogue_f")
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Zip(zip).withZipExtensions().extract(dir)
    assertThat(dir.resolve("links/ok")).isSymbolicLink().hasSameBinaryContentAs(dir.resolve("f"))
    assertThat(dir.resolve("rogue")).isSymbolicLink().hasSameBinaryContentAs(rogueTarget.toPath())
  }

  @Test fun zipRogueSymlinks() {
    assumeSymLinkCreationIsSupported()

    val zip = tempDir.newFile("test.zip")
    ZipArchiveOutputStream(FileOutputStream(zip)).use { writeEntry(it, "rogue", link = "../f") }

    val decompressor = Decompressor.Zip(zip).withZipExtensions().allowEscapingSymlinks(false)
    val dir = tempDir.newDirectory("unpacked").toPath()
    testNoTraversal(decompressor, dir, dir.resolve("rogue"))
  }

  @Test fun tarRogueSymlinks() {
    assumeSymLinkCreationIsSupported()

    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use { writeEntry(it, "rogue", link = "../f") }

    val decompressor = Decompressor.Tar(tar).allowEscapingSymlinks(false)
    val dir = tempDir.newDirectory("unpacked").toPath()
    testNoTraversal(decompressor, dir, dir.resolve("rogue"))
  }

  @Test fun prefixPathsFilesInZip() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use {
      writeEntry(it, "a/b/c.txt")
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Zip(zip).removePrefixPath("a/b").extract(dir)

    assertThat(dir.resolve("c.txt")).isRegularFile()
    assertThat(dir.resolve("a")).doesNotExist()
    assertThat(dir.resolve("a/b")).doesNotExist()
    assertThat(dir.resolve("b")).doesNotExist()
  }

  @Test fun prefixPathsFilesInCommonsZip() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use {
      writeEntry(it, "a/b/c.txt")
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Zip(zip).removePrefixPath("a/b").extract(dir)

    assertThat(dir.resolve("c.txt")).isRegularFile()
    assertThat(dir.resolve("a")).doesNotExist()
    assertThat(dir.resolve("a/b")).doesNotExist()
    assertThat(dir.resolve("b")).doesNotExist()
  }

  @Test fun prefixPathFilesInZipWithFilter() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use {
      writeEntry(it, "a/b/c.txt")
      writeEntry(it, "skip.txt")
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    val filterLog = mutableListOf<String>()
    Decompressor.Zip(zip).removePrefixPath("a/b").filter(Predicate{  filterLog.add(it) }).extract(dir)

    assertThat(dir.resolve("c.txt")).isRegularFile()
    assertThat(dir.resolve("a")).doesNotExist()
    assertThat(dir.resolve("a/b")).doesNotExist()
    assertThat(dir.resolve("b")).doesNotExist()

    assertThat(filterLog).containsExactlyInAnyOrder("a/b/c.txt", "skip.txt")
  }

  @Test fun prefixPathsFilesInTarWithSymlinks() {
    assumeSymLinkCreationIsSupported()

    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use {
      writeEntry(it, "a/f")
      writeEntry(it, "a/links/ok", link = "../f")
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Tar(tar).removePrefixPath("a").extract(dir)

    assertThat(dir.resolve("f")).isRegularFile()
    assertThat(dir.resolve("links/ok")).isSymbolicLink().hasSameBinaryContentAs(dir.resolve("f"))
  }

  @Test fun prefixPathFillMatch() {
    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use {
      writeEntry(it, "./a/f")
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Tar(tar).removePrefixPath("/a/f").extract(dir)

    assertThat(dir.resolve("f")).doesNotExist()
  }

  @Test fun prefixPathWithSlashTar() {
    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use {
      writeEntry(it, "./a/f")
      writeEntry(it, "/a/g")
      writeEntry(it, "././././././//a/h")
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Tar(tar).removePrefixPath("/a/").extract(dir)

    assertThat(dir.resolve("f")).isRegularFile()
    assertThat(dir.resolve("g")).isRegularFile()
    assertThat(dir.resolve("h")).isRegularFile()
  }

  @Test fun prefixPathWithDotTar() {
    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use {
      writeEntry(it, "./a/b/f")
      writeEntry(it, "/a/b/g")
      writeEntry(it, "././././././//a/b/h")
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Tar(tar).removePrefixPath("./a/b").extract(dir)

    assertThat(dir.resolve("f")).isRegularFile()
    assertThat(dir.resolve("g")).isRegularFile()
    assertThat(dir.resolve("h")).isRegularFile()
  }

  @Test fun prefixPathWithCommonsZip() {
    val zip = tempDir.newFile("test.zip")
    ZipArchiveOutputStream(FileOutputStream(zip)).use {
      writeEntry(it, "./a/b/f")
      writeEntry(it, "/a/b/g")
      writeEntry(it, "././././././//a/b/h")
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Zip(zip).removePrefixPath("./a/b").extract(dir)

    assertThat(dir.resolve("f")).isRegularFile()
    assertThat(dir.resolve("g")).isRegularFile()
    assertThat(dir.resolve("h")).isRegularFile()
  }

  @Test fun prefixPathTarSymlink() {
    assumeSymLinkCreationIsSupported()

    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use {
      writeEntry(it, "./a/b/f")
      writeEntry(it, "a/b/links/ok", link = "../f")
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Tar(tar).removePrefixPath("a/b").extract(dir)

    assertThat(dir.resolve("f")).isRegularFile()
    assertThat(dir.resolve("links/ok")).isSymbolicLink().hasSameBinaryContentAs(dir.resolve("f"))
  }

  @Test fun prefixPathZipSymlink() {
    assumeSymLinkCreationIsSupported()

    val zip = tempDir.newFile("test.zip")
    ZipArchiveOutputStream(FileOutputStream(zip)).use {
      writeEntry(it, "./a/b/f")
      writeEntry(it, "a/b/links/ok", link = "../f")
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Zip(zip).withZipExtensions().removePrefixPath("a/b").extract(dir)

    assertThat(dir.resolve("f")).isRegularFile()
    assertThat(dir.resolve("links/ok")).isSymbolicLink().hasSameBinaryContentAs(dir.resolve("f"))
  }

  @Test fun prefixPathTarRogueSymlinks() {
    assumeSymLinkCreationIsSupported()

    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use { writeEntry(it, "a/b/c/rogue", link = "../f") }

    val decompressor = Decompressor.Tar(tar).allowEscapingSymlinks(false).removePrefixPath("a/b/c")
    val dir = tempDir.newDirectory("unpacked").toPath()
    testNoTraversal(decompressor, dir, dir.resolve("rogue"))
  }

  @Test fun prefixPathZipRogueSymlinks() {
    assumeSymLinkCreationIsSupported()

    val zip = tempDir.newFile("test.zip")
    ZipArchiveOutputStream(FileOutputStream(zip)).use { writeEntry(it, "a/b/c/rogue", link = "../f") }

    val decompressor = Decompressor.Zip(zip).withZipExtensions().allowEscapingSymlinks(false).removePrefixPath("a/b/c")
    val dir = tempDir.newDirectory("unpacked").toPath()
    testNoTraversal(decompressor, dir, dir.resolve("rogue"))
  }

  @Test fun prefixPathSkipsTooShortPaths() {
    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use {
      writeEntry(it, "missed")
      writeEntry(it, "a/b/c/file.txt")
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Tar(tar).removePrefixPath("a/b").extract(dir)
    assertThat(dir.resolve("c/file.txt")).isRegularFile()
    assertThat(dir.resolve("missed")).doesNotExist()
  }

  @Test fun fileOverwrite() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use { writeEntry(it, "a/file.txt") }
    val dir = tempDir.newDirectory("unpacked")
    val file = tempDir.newFile("unpacked/a/file.txt", byteArrayOf(0))
    Decompressor.Zip(zip).extract(dir)
    assertThat(file).hasBinaryContent(TestContent)
  }

  @Test fun symlinkOverwrite() {
    assumeSymLinkCreationIsSupported()

    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use {
      writeEntry(it, "a/file")
      writeEntry(it, "a/link", link = "file")
    }
    val dir = tempDir.newDirectory("unpacked")
    val link = tempDir.rootPath.resolve("unpacked/a/link")
    val target = tempDir.newFile("unpacked/a/target", byteArrayOf(0)).toPath()
    Files.createSymbolicLink(link, target)
    Decompressor.Tar(tar).extract(dir)
    assertThat(link).isSymbolicLink().hasBinaryContent(TestContent)
  }

  @Test fun extZipPureNIO() {
    MemoryFileSystemBuilder.newLinux().build("${DecompressorTest::class.simpleName}.extZipPureNIO").use { fs ->
      val testDir = fs.getPath("/home/${SystemProperties.getUserName()}")
      val zip = Files.createFile(testDir.resolve("test.zip"))
      ZipArchiveOutputStream(Files.newOutputStream(zip)).use {
        writeEntry(it, "dir/r", mode = 0b100_000_000)
      }
      val dir = Files.createDirectory(testDir.resolve("unpacked"))
      Decompressor.Zip(zip).withZipExtensions().extract(dir)
      assertThat(dir.resolve("dir/r")).exists()
    }
  }

  @Test fun tarPureNIO() {
    MemoryFileSystemBuilder.newLinux().build("${DecompressorTest::class.simpleName}.tarPureNIO").use { fs ->
      val testDir = fs.getPath("/home/${SystemProperties.getUserName()}")
      val tar = Files.createFile(testDir.resolve("test.tar"))
      TarArchiveOutputStream(Files.newOutputStream(tar)).use {
        writeEntry(it, "dir/r", mode = 0b100_000_000)
      }
      val dir = Files.createDirectory(testDir.resolve("unpacked"))
      Decompressor.Tar(tar).extract(dir)
      assertThat(dir.resolve("dir/r")).exists()
    }
  }

  //<editor-fold desc="Helpers.">
  private fun writeEntry(zip: ZipOutputStream, name: String) {
    val entry = ZipEntry(name)
    entry.time = System.currentTimeMillis()
    zip.putNextEntry(entry)
    zip.write(TestContent)
    zip.closeEntry()
  }

  private fun writeEntry(tar: TarArchiveOutputStream, name: String, mode: Int = 0, link: String? = null) {
    if (link != null) {
      val entry = TarArchiveEntry(name, TarArchiveEntry.LF_SYMLINK)
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
    private val Writable = Condition<Path>(Predicate { Files.isWritable(it) }, "writable")
    private val Executable = Condition<Path>(Predicate { Files.isExecutable(it) }, "executable")
    private val Hidden = Condition<Path>(Predicate { Files.isHidden(it) }, "hidden")
  }
  //</editor-fold>
}
