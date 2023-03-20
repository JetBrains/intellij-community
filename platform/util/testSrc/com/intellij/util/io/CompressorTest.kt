// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.openapi.util.io.IoTestUtil.assumeSymLinkCreationIsSupported
import com.intellij.openapi.util.io.NioFiles
import com.intellij.testFramework.rules.TempDirectory
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.io.path.name

class CompressorTest {
  @Rule @JvmField var tempDir = TempDirectory()

  @Test fun simpleZip() {
    val zip = tempDir.newFile("test.zip")
    val data = tempDir.newFile("file.txt", "789".toByteArray())
    Compressor.Zip(zip).use {
      it.addFile("empty.txt", byteArrayOf())
      it.addFile("file1.txt", "123".toByteArray())
      it.addFile("file2.txt", ByteArrayInputStream("456".toByteArray()))
      it.addFile("file3.txt", data)
    }
    assertZip(zip, "empty.txt" to "", "file1.txt" to "123", "file2.txt" to "456", "file3.txt" to "789")
  }

  @Test fun simpleTar() {
    val tar = tempDir.newFile("test.tar")
    val data = tempDir.newFile("file.txt", "789".toByteArray())
    Compressor.Tar(tar, Compressor.Tar.Compression.GZIP).use {
      it.addFile("empty.txt", byteArrayOf())
      it.addFile("file1.txt", "123".toByteArray())
      it.addFile("file2.txt", ByteArrayInputStream("456".toByteArray()))
      it.addFile("file3.txt", data)
    }
    assertTar(tar, "empty.txt" to "", "file1.txt" to "123", "file2.txt" to "456", "file3.txt" to "789")
  }

  @Test fun simpleZipWithFilters() {
    val zip = tempDir.newFile("test.zip")
    val set = mutableSetOf<String>()
    Compressor.Zip(zip).filter { entryName, _ -> set.add(entryName) && !entryName.startsWith("d1/") }.use {
      it.addFile("file1.txt", "123".toByteArray())
      it.addFile("file2.txt", "456".toByteArray())
      it.addFile("file1.txt", "789".toByteArray())
      it.addFile("d1/d11/f.txt", "-".toByteArray())
      it.addDirectory("d1/d12")
    }
    assertZip(zip, "file1.txt" to "123", "file2.txt" to "456")
  }

  @Test fun streamZip() {
    val zip = tempDir.newFile("test.zip")
    FileOutputStream(zip).use { os ->
      Compressor.Zip(os).withLevel(ZipEntry.STORED).use {
        it.addFile("file.txt", "123".toByteArray())
      }
    }
    assertZip(zip, "file.txt" to "123")
  }

  @Test fun recursiveZip() {
    val dir = tempDir.newDirectory("dir")
    tempDir.newFile("dir/f1").writeText("1")
    tempDir.newFile("dir/f2").writeText("2")
    tempDir.newFile("dir/d1/f11").writeText("11")
    tempDir.newFile("dir/d1/f12").writeText("12")
    tempDir.newFile("dir/d1/d11/f111").writeText("111")
    tempDir.newFile("dir/d1/d11/f112").writeText("112")
    tempDir.newFile("dir/d2/f21").writeText("21")
    tempDir.newFile("dir/d2/f22").writeText("22")

    val zip = tempDir.newFile("test.zip")
    Compressor.Zip(zip).filter { entryName, _ -> entryName != "d1/d11" }.use { it.addDirectory(dir) }
    assertZip(
      zip,
      "d1/" to "", "d2/" to "",
      "f1" to "1", "f2" to "2",
      "d1/f11" to "11", "d1/f12" to "12",
      "d2/f21" to "21", "d2/f22" to "22")
  }

  @Test fun recursiveTarWithPrefix() {
    val dir = tempDir.newDirectory("dir")
    tempDir.newFile("dir/f1").writeText("1")
    tempDir.newFile("dir/f2").writeText("2")
    tempDir.newFile("dir/d1/f11").writeText("11")
    tempDir.newFile("dir/d1/f12").writeText("12")
    tempDir.newFile("dir/d1/d11/f111").writeText("111")
    tempDir.newFile("dir/d1/d11/f112").writeText("112")
    tempDir.newFile("dir/d2/f21").writeText("21")
    tempDir.newFile("dir/d2/f22").writeText("22")

    val tar = tempDir.newFile("test.tgz")
    Compressor.Tar(tar, Compressor.Tar.Compression.GZIP).use { it.addDirectory("tar/", dir) }
    assertTar(
      tar,
      "tar/" to "", "tar/d1/" to "", "tar/d1/d11/" to "", "tar/d2/" to "",
      "tar/f1" to "1", "tar/f2" to "2",
      "tar/d1/f11" to "11", "tar/d1/f12" to "12",
      "tar/d1/d11/f111" to "111", "tar/d1/d11/f112" to "112",
      "tar/d2/f21" to "21", "tar/d2/f22" to "22")
  }

  @Test fun tarWithEmptyPrefix() {
    val file = tempDir.newFile("dir/file").toPath()
    val tar = tempDir.newFile("test.tgz")
    Compressor.Tar(tar, Compressor.Tar.Compression.GZIP).use { it.addDirectory("", file.parent) }
    assertTar(tar, file.name to "")
  }

  @Test fun tarWithExecutableFiles() {
    assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))

    val dir = tempDir.newDirectory("dir").toPath()
    val regular = Files.createFile(dir.resolve("regular"))
    val executable = Files.createFile(dir.resolve("executable"), PosixFilePermissions.asFileAttribute(PosixFilePermission.values().toSet()))

    val tar = tempDir.newFile("test.tgz")
    Compressor.Tar(tar, Compressor.Tar.Compression.GZIP).use { it.addDirectory(dir) }
    val out = tempDir.newDirectory("out").toPath()
    Decompressor.Tar(tar).extract(out)
    assertThat(Files.getPosixFilePermissions(out.resolve(regular.name))).doesNotContain(PosixFilePermission.OWNER_EXECUTE)
    assertThat(Files.getPosixFilePermissions(out.resolve(executable.name))).contains(PosixFilePermission.OWNER_EXECUTE)
  }

  @Test fun tarWithSymbolicLinks() {
    assumeSymLinkCreationIsSupported()

    val dir = tempDir.newDirectory("dir").toPath()
    val origin = Files.createFile(dir.resolve("origin"))
    val link = Files.createSymbolicLink(dir.resolve("link"), origin.fileName)

    val tar = tempDir.newFile("test.tgz")
    Compressor.Tar(tar, Compressor.Tar.Compression.GZIP).use { it.addDirectory(dir) }
    NioFiles.deleteRecursively(dir)

    val out = tempDir.newDirectory("out").toPath()
    Decompressor.Tar(tar).extract(out)
    assertThat(out.resolve(link.name)).isSymbolicLink.hasSameBinaryContentAs(out.resolve(origin.name))
  }

  @Test fun entryNameTrimming() {
    val zip = tempDir.newFile("test.zip")
    Compressor.Zip(zip).use { it.addFile("//file.txt//", "123".toByteArray()) }
    assertZip(zip, "file.txt" to "123")
  }

  @Test fun jarWithManifest() {
    val jar = tempDir.newFile("test.jar")
    val mf = Manifest()
    mf.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "9.75"
    Compressor.Jar(jar).use { it.addManifest(mf) }
    assertZip(jar, JarFile.MANIFEST_NAME to "Manifest-Version: 9.75")
  }

  private fun assertZip(zip: File, vararg expected: Pair<String, String>) {
    val actual = ZipInputStream(FileInputStream(zip)).use {
      generateSequence(it::getNextEntry).map { entry -> entry.name to it.readBytes().toString(StandardCharsets.UTF_8).trim() }.toList()
    }
    assertThat(actual).containsExactlyInAnyOrder(*expected)
  }

  private fun assertTar(tar: File, vararg expected: Pair<String, String>) {
    val actual = TarArchiveInputStream(GzipCompressorInputStream(FileInputStream(tar))).use {
      generateSequence(it::getNextTarEntry).map { entry -> entry.name to it.readBytes().toString(StandardCharsets.UTF_8).trim() }.toList()
    }
    assertThat(actual).containsExactlyInAnyOrder(*expected)
  }
}
