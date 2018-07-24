// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.testFramework.rules.TempDirectory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.zip.ZipEntry
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

  @Test fun noInternalTraversalInTar() {
    val tar = tempDir.newFile("test.tgz")
    TarArchiveOutputStream(GzipCompressorOutputStream(FileOutputStream(tar))).use { writeEntry(it, "a/../bad.txt") }
    val dir = tempDir.newFolder("unpacked")
    testNoTraversal(Decompressor.Tar(tar), dir, File(dir, "bad.txt"))
  }

  @Test fun noExternalTraversalInTar() {
    val tar = tempDir.newFile("test.tgz")
    TarArchiveOutputStream(GzipCompressorOutputStream(FileOutputStream(tar))).use { writeEntry(it, "../evil.txt") }
    val dir = tempDir.newFolder("unpacked")
    testNoTraversal(Decompressor.Tar(tar), dir, File(dir.parent, "evil.txt"))
  }

  @Test fun noAbsolutePathsInTar() {
    val tar = tempDir.newFile("test.tgz")
    TarArchiveOutputStream(GzipCompressorOutputStream(FileOutputStream(tar))).use { writeEntry(it, "/root.txt") }
    val dir = tempDir.newFolder("unpacked")
    Decompressor.Tar(tar).extract(dir)
    assertThat(File(dir, "root.txt")).exists()
  }

  private fun writeEntry(zip: ZipOutputStream, name: String) {
    val entry = ZipEntry(name)
    entry.time = System.currentTimeMillis()
    zip.putNextEntry(entry)
    zip.write('-'.toInt())
    zip.closeEntry()
  }

  private fun writeEntry(tar: TarArchiveOutputStream, name: String) {
    val entry = TarArchiveEntry(name)
    entry.modTime = Date()
    entry.size = 1
    tar.putArchiveEntry(entry)
    tar.write('-'.toInt())
    tar.closeArchiveEntry()
  }

  private fun testNoTraversal(decompressor: Decompressor<*>, dir: File, unexpected: File) {
    val error = try {
      decompressor.extract(dir)
      null
    }
    catch (e: IOException) { e }

    assertThat(unexpected).doesNotExist()
    assertThat(error?.message).contains(unexpected.name)
  }
}