// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.testFramework.rules.TempDirectory
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

class CompressorTest {
  @Rule @JvmField var tempDir = TempDirectory()

  @Test fun simpleZip() {
    val zip = tempDir.newFile("test.zip")
    Compressor.Zip(zip).use { it.addFile("file.txt", "123".toByteArray()) }
    assertZip(zip, "file.txt" to "123")
  }

  @Test fun simpleZipWithFilters() {
    val zip = tempDir.newFile("test.zip")
    val set = mutableSetOf<String>()
    Compressor.Zip(zip).filter(set::add).use {
      it.addFile("file1.txt", "123".toByteArray())
      it.addFile("file2.txt", "456".toByteArray())
      it.addFile("file1.txt", "789".toByteArray())
    }
    assertZip(zip, "file1.txt" to "123", "file2.txt" to "456")
  }

  @Test fun recursiveZip() {
    val dir = tempDir.newFolder("dir")
    tempDir.newFile("dir/f1").writeText("1")
    tempDir.newFile("dir/f2").writeText("2")
    tempDir.newFile("dir/d1/f11").writeText("11")
    tempDir.newFile("dir/d1/f12").writeText("12")
    tempDir.newFile("dir/d1/d11/f111").writeText("111")
    tempDir.newFile("dir/d1/d11/f112").writeText("112")
    tempDir.newFile("dir/d2/f21").writeText("21")
    tempDir.newFile("dir/d2/f22").writeText("22")

    val zip = tempDir.newFile("test.zip")
    Compressor.Zip(zip).use { it.addDirectory(dir) }
    assertZip(
      zip,
      "d1/" to "", "d1/d11/" to "", "d2/" to "",
      "f1" to "1", "f2" to "2",
      "d1/f11" to "11", "d1/f12" to "12",
      "d1/d11/f111" to "111", "d1/d11/f112" to "112",
      "d2/f21" to "21", "d2/f22" to "22")
  }

  @Test fun recursiveTarWithPrefix() {
    val dir = tempDir.newFolder("dir")
    tempDir.newFile("dir/f1").writeText("1")
    tempDir.newFile("dir/f2").writeText("2")
    tempDir.newFile("dir/d1/f11").writeText("11")
    tempDir.newFile("dir/d1/f12").writeText("12")
    tempDir.newFile("dir/d1/d11/f111").writeText("111")
    tempDir.newFile("dir/d1/d11/f112").writeText("112")
    tempDir.newFile("dir/d2/f21").writeText("21")
    tempDir.newFile("dir/d2/f22").writeText("22")

    val tar = tempDir.newFile("test.tgz")
    Compressor.Tar(tar).use { it.addDirectory("tar/", dir) }
    assertTar(
      tar,
      "tar/" to "", "tar/d1/" to "", "tar/d1/d11/" to "", "tar/d2/" to "",
      "tar/f1" to "1", "tar/f2" to "2",
      "tar/d1/f11" to "11", "tar/d1/f12" to "12",
      "tar/d1/d11/f111" to "111", "tar/d1/d11/f112" to "112",
      "tar/d2/f21" to "21", "tar/d2/f22" to "22")
  }

  @Test fun entryNameTrimming() {
    val zip = tempDir.newFile("test.zip")
    Compressor.Zip(zip).use { it.addFile("//file.txt//", "123".toByteArray()) }
    assertZip(zip, "file.txt" to "123")
  }

  private fun assertZip(zip: File, vararg expected: Pair<String, String>) {
    val actual = ZipInputStream(FileInputStream(zip)).use {
      generateSequence(it::getNextEntry).map { entry -> entry.name to String(it.readBytes()) }.toList()
    }
    assertThat(actual).containsExactlyInAnyOrder(*expected)
  }

  private fun assertTar(tar: File, vararg expected: Pair<String, String>) {
    val actual = TarArchiveInputStream(GzipCompressorInputStream(FileInputStream(tar))).use {
      generateSequence(it::getNextTarEntry).map { entry -> entry.name to String(it.readBytes()) }.toList()
    }
    assertThat(actual).containsExactlyInAnyOrder(*expected)
  }
}