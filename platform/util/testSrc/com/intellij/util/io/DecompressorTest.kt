// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.IoTestUtil.assumeSymLinkCreationIsSupported
import com.intellij.testFramework.rules.TempDirectory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Condition
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
    val dir = tempDir.newFolder("unpacked")
    testNoTraversal(Decompressor.Zip(zip), dir, File(dir, "bad.txt"))
  }

  @Test fun noExternalTraversalInZip() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use { writeEntry(it, "../evil.txt") }
    val dir = tempDir.newFolder("unpacked")
    testNoTraversal(Decompressor.Zip(zip), dir, File(dir.parent, "evil.txt"))
  }

  @Test fun noAbsolutePathsInZip() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use { writeEntry(it, "/root.txt") }
    val dir = tempDir.newFolder("unpacked")
    Decompressor.Zip(zip).extract(dir)
    assertThat(File(dir, "root.txt")).exists()
  }

  @Test fun tarDetectionPlain() {
    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use { writeEntry(it, "dir/file.txt") }
    val dir = tempDir.newFolder("unpacked")
    Decompressor.Tar(tar).extract(dir)
    assertThat(File(dir, "dir/file.txt")).exists()
  }

  @Test fun tarDetectionGZip() {
    val tar = tempDir.newFile("test.tgz")
    TarArchiveOutputStream(GzipCompressorOutputStream(FileOutputStream(tar))).use { writeEntry(it, "dir/file.txt") }
    val dir = tempDir.newFolder("unpacked")
    Decompressor.Tar(tar).extract(dir)
    assertThat(File(dir, "dir/file.txt")).exists()
  }

  @Test fun noInternalTraversalInTar() {
    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use { writeEntry(it, "a/../bad.txt") }
    val dir = tempDir.newFolder("unpacked")
    testNoTraversal(Decompressor.Tar(tar), dir, File(dir, "bad.txt"))
  }

  @Test fun noExternalTraversalInTar() {
    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use { writeEntry(it, "../evil.txt") }
    val dir = tempDir.newFolder("unpacked")
    testNoTraversal(Decompressor.Tar(tar), dir, File(dir.parent, "evil.txt"))
  }

  @Test fun noAbsolutePathsInTar() {
    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use { writeEntry(it, "/root.txt") }
    val dir = tempDir.newFolder("unpacked")
    Decompressor.Tar(tar).extract(dir)
    assertThat(File(dir, "root.txt")).exists()
  }

  @Test(expected = ZipException::class)
  fun failsOnCorruptedZip() {
    val zip = tempDir.newFile("test.zip")
    zip.writeText("whatever")
    val dir = tempDir.newFolder("unpacked")
    Decompressor.Zip(zip).extract(dir)
  }

  @Test fun tarFileModes() {
    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use {
      writeEntry(it, "dir/r", mode = 0b100_000_000)
      writeEntry(it, "dir/rw", mode = 0b110_000_000)
      writeEntry(it, "dir/rx", mode = 0b101_000_000)
      writeEntry(it, "dir/rwx", mode = 0b111_000_000)
    }
    val dir = tempDir.newFolder("unpacked")
    Decompressor.Tar(tar).extract(dir)
    assertThat(File(dir, "dir/r")).exists().isNot(Writable).let { if (SystemInfo.isUnix) it.isNot(Executable) }
    assertThat(File(dir, "dir/rw")).exists().`is`(Writable).let { if (SystemInfo.isUnix) it.isNot(Executable) }
    assertThat(File(dir, "dir/rx")).exists().isNot(Writable).`is`(Executable)
    assertThat(File(dir, "dir/rwx")).exists().`is`(Writable).`is`(Executable)
  }

  @Test fun filtering() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use {
      writeEntry(it, "d1/f1.txt")
      writeEntry(it, "d2/f2.txt")
    }
    val dir = tempDir.newFolder("unpacked")
    Decompressor.Zip(zip).filter { !it.startsWith("d2/") }.extract(dir)
    assertThat(File(dir, "d1/f1.txt")).isFile()
    assertThat(File(dir, "d2")).doesNotExist()
  }

  @Test fun symlinks() {
    assumeSymLinkCreationIsSupported()

    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use {
      writeEntry(it, "f")
      writeEntry(it, "links/ok", link = "../f")
    }
    val dir = tempDir.newFolder("unpacked")
    Decompressor.Tar(tar).withSymlinks().extract(dir)
    assertThat(File(dir, "links/ok").toPath()).isSymbolicLink().hasSameContentAs(File(dir, "f").toPath())
  }

  @Test fun rogueSymlinks() {
    assumeSymLinkCreationIsSupported()

    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use { writeEntry(it, "rogue", link = "../f") }
    val dir = tempDir.newFolder("unpacked")
    testNoTraversal(Decompressor.Tar(tar).withSymlinks(), dir, File(dir, "rogue"))
  }

  @Test fun cutDirsFilesInZip() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use { writeEntry(it, "a/b/c.txt") }
    val dir = tempDir.newFolder("unpacked")
    Decompressor.Zip(zip).cutDirs(2).extract(dir)

    assertThat(File(dir, "c.txt")).isFile()
    assertThat(File(dir, "a")).doesNotExist()
    assertThat(File(dir, "a/b")).doesNotExist()
    assertThat(File(dir, "b")).doesNotExist()
  }

  @Test fun cutDirsFilesInZipWitFilter() {
    val zip = tempDir.newFile("test.zip")
    ZipOutputStream(FileOutputStream(zip)).use {
      writeEntry(it, "a/b/c.txt")
      writeEntry(it, "skip.txt")
    }
    val dir = tempDir.newFolder("unpacked")
    val filterLog = mutableListOf<String>()
    Decompressor.Zip(zip).cutDirs(2).filter { filterLog.add(it) }.extract(dir)

    assertThat(File(dir, "c.txt")).isFile()
    assertThat(File(dir, "a")).doesNotExist()
    assertThat(File(dir, "a/b")).doesNotExist()
    assertThat(File(dir, "b")).doesNotExist()

    //it must call filter before cut-dirs
    Assert.assertEquals(setOf("a/b/c.txt", "skip.txt"), filterLog.toSet())
  }

  @Test fun cutDirsFilesInTarWithSymlinks() {
    assumeSymLinkCreationIsSupported()

    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use {
      writeEntry(it, "a/f")
      writeEntry(it, "a/links/ok", link = "../f")
    }
    val dir = tempDir.newFolder("unpacked")
    Decompressor.Tar(tar).cutDirs(1).extract(dir)

    assertThat(File(dir, "f")).isFile()
    assertThat(File(dir, "links/ok").toPath()).isSymbolicLink().hasSameContentAs(File(dir, "f").toPath())
  }

  @Test fun cutDirRogueSymlinks() {
    assumeSymLinkCreationIsSupported()

    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use { writeEntry(it, "a/b/c/rogue", link = "../f") }
    val dir = tempDir.newFolder("unpacked")
    testNoTraversal(Decompressor.Tar(tar).cutDirs(3), dir, File(dir, "rogue"))
  }

  @Test fun cutDirSkipsTooShortPaths() {
    assumeSymLinkCreationIsSupported()

    val tar = tempDir.newFile("test.tar")
    TarArchiveOutputStream(FileOutputStream(tar)).use {
      writeEntry(it, "missed")
      writeEntry(it, "a/b/c/file.txt")
    }
    val dir = tempDir.newFolder("unpacked")
    Decompressor.Tar(tar).cutDirs(2).extract(dir)
    assertThat(File(dir, "c/file.txt")).isFile()
    assertThat(File(dir, "missed")).doesNotExist()
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

  private fun testNoTraversal(decompressor: Decompressor, dir: File, unexpected: File) {
    val error = try {
      decompressor.extract(dir)
      null
    }
    catch (e: IOException) { e }

    assertThat(unexpected.toPath()).doesNotExist()
    assertThat(error?.message).contains(unexpected.name)
  }

  companion object {
    private val Writable = Condition<File>(Predicate { it.canWrite() }, "writable")
    private val Executable = Condition<File>(Predicate { it.canExecute() }, "executable")
  }
}