// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.IoTestUtil.assumeNioSymLinkCreationIsSupported
import com.intellij.testFramework.rules.TempDirectory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.UnixStat
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Condition
import org.junit.Rule
import org.junit.Test
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.*
import java.util.function.Predicate
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

@Suppress("UsePropertyAccessSyntax")
class DecompressorTest {
  @Rule @JvmField var tempDir = TempDirectory()

  @Test fun noInternalTraversalInZip() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use { writeEntry(it, "a/../bad.txt") }
    val dir = tempDir.newDirectory("unpacked").toPath()
    testNoTraversal(Decompressor.Zip(zip), dir, dir.resolve("bad.txt"))
  }

  @Test fun noInternalTraversalInCommonsZip() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use { writeEntry(it, "a/../bad.txt") }
    val dir = tempDir.newDirectory("unpacked").toPath()
    testNoTraversal(Decompressor.Zip(zip).withUnixPermissionsAndSymlinks(), dir, dir.resolve("bad.txt"))
  }

  @Test fun noExternalTraversalInZip() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use { writeEntry(it, "../evil.txt") }
    val dir = tempDir.newDirectory("unpacked").toPath()
    testNoTraversal(Decompressor.Zip(zip), dir, dir.parent.resolve("evil.txt"))
  }

  @Test fun noExternalTraversalInCommons() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use { writeEntry(it, "../evil.txt") }
    val dir = tempDir.newDirectory("unpacked").toPath()
    testNoTraversal(Decompressor.Zip(zip).withUnixPermissionsAndSymlinks(), dir, dir.parent.resolve("evil.txt"))
  }

  @Test fun noAbsolutePathsInZip() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use { writeEntry(it, "/root.txt") }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Zip(zip).extract(dir)
    assertThat(dir.resolve("root.txt")).exists()
  }

  @Test fun noAbsolutePathsInCommonsZip() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use { writeEntry(it, "/root.txt") }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Zip(zip).withUnixPermissionsAndSymlinks().extract(dir)
    assertThat(dir.resolve("root.txt")).exists()
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

  @Test(expected = ZipException::class)
  fun failsOnCorruptedCommonsZip() {
    val zip = tempDir.newFile("test.zip")
    zip.writeText("whatever")
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Zip(zip).withUnixPermissionsAndSymlinks().extract(dir)
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
    assertThat(dir.resolve("dir/r")).exists().isNot(Writable).let { if (SystemInfo.isUnix) it.isNot(Executable) }
    assertThat(dir.resolve("dir/rw")).exists().`is`(Writable).let { if (SystemInfo.isUnix) it.isNot(Executable) }
    assertThat(dir.resolve("dir/rx")).exists().isNot(Writable).`is`(Executable)
    assertThat(dir.resolve("dir/rwx")).exists().`is`(Writable).`is`(Executable)
  }

  @Test fun zipFileModes() {
    val zip = tempDir.newFile("test.zip")
    ZipArchiveOutputStream(FileOutputStream(zip)).use {
      writeEntry(it, "dir/r", mode = 0b100_000_000)
      writeEntry(it, "dir/rw", mode = 0b110_000_000)
      writeEntry(it, "dir/rx", mode = 0b101_000_000)
      writeEntry(it, "dir/rwx", mode = 0b111_000_000)
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Zip(zip).withUnixPermissionsAndSymlinks().extract(dir)
    assertThat(dir.resolve("dir/r")).exists().isNot(Writable).let { if (SystemInfo.isUnix) it.isNot(Executable) }
    assertThat(dir.resolve("dir/rw")).exists().`is`(Writable).let { if (SystemInfo.isUnix) it.isNot(Executable) }
    assertThat(dir.resolve("dir/rx")).exists().isNot(Writable).`is`(Executable)
    assertThat(dir.resolve("dir/rwx")).exists().`is`(Writable).`is`(Executable)
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
    assumeNioSymLinkCreationIsSupported()

    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use {
      writeEntry(it, "f")
      writeEntry(it, "links/ok", link = "../f")
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Tar(tar).withSymlinks().extract(dir)
    assertThat(dir.resolve("links/ok")).isSymbolicLink().hasSameBinaryContentAs(dir.resolve("f"))
  }

  @Test fun zipSymlinks() {
    assumeNioSymLinkCreationIsSupported()

    val zip = tempDir.newFile("test.zip")
    ZipArchiveOutputStream(FileOutputStream(zip)).use {
      writeEntry(it, "f")
      writeEntry(it, "links/ok", link = "../f")
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Zip(zip).withUnixPermissionsAndSymlinks().extract(dir)
    assertThat(dir.resolve("links/ok")).isSymbolicLink().hasSameBinaryContentAs(dir.resolve("f"))
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
    Decompressor.Zip(zip).withUnixPermissionsAndSymlinks().removePrefixPath("a/b").extract(dir)

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
    assumeNioSymLinkCreationIsSupported()

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
    Decompressor.Zip(zip).withUnixPermissionsAndSymlinks().removePrefixPath("./a/b").extract(dir)

    assertThat(dir.resolve("f")).isRegularFile()
    assertThat(dir.resolve("g")).isRegularFile()
    assertThat(dir.resolve("h")).isRegularFile()
  }

  @Test fun prefixPathTarSymlink() {
    assumeNioSymLinkCreationIsSupported()

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
    assumeNioSymLinkCreationIsSupported()

    val zip = tempDir.newFile("test.zip")
    ZipArchiveOutputStream(FileOutputStream(zip)).use {
      writeEntry(it, "./a/b/f")
      writeEntry(it, "a/b/links/ok", link = "../f")
    }
    val dir = tempDir.newDirectory("unpacked").toPath()
    Decompressor.Zip(zip).withUnixPermissionsAndSymlinks().removePrefixPath("a/b").extract(dir)

    assertThat(dir.resolve("f")).isRegularFile()
    assertThat(dir.resolve("links/ok")).isSymbolicLink().hasSameBinaryContentAs(dir.resolve("f"))
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

  private fun writeEntry(zip: ZipOutputStream, name: String) {
    val entry = ZipEntry(name)
    entry.time = System.currentTimeMillis()
    zip.putNextEntry(entry)
    zip.write('-'.toInt())
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
      entry.size = 1
      if (mode != 0) entry.mode = mode
      tar.putArchiveEntry(entry)
      tar.write('-'.toInt())
    }
    tar.closeArchiveEntry()
  }

  private fun writeEntry(zip: ZipArchiveOutputStream, name: String, mode: Int = 0, link: String? = null) {
    val entry = ZipArchiveEntry(name)
    entry.lastModifiedTime = FileTime.from(Instant.now())

    if (link != null) {
      entry.lastModifiedTime = FileTime.from(Instant.now())
      entry.unixMode = UnixStat.LINK_FLAG
      zip.putArchiveEntry(entry)
      zip.write(link.toByteArray(Charsets.UTF_8))
    }
    else {
      entry.size = 1
      if (mode != 0) entry.unixMode = mode
      zip.putArchiveEntry(entry)
      zip.write('-'.toInt())
    }
    zip.closeArchiveEntry()
  }

  private fun testNoTraversal(decompressor: Decompressor, dir: Path, unexpected: Path) {
    val error = try {
      decompressor.extract(dir)
      null
    }
    catch (e: IOException) { e }

    assertThat(unexpected).doesNotExist()
    assertThat(error?.message).contains(unexpected.fileName.toString())
  }

  companion object {
    private val Writable = Condition<Path>(Predicate { Files.isWritable(it) }, "writable")
    private val Executable = Condition<Path>(Predicate { Files.isExecutable(it) }, "executable")
  }
}