// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.showcase

import com.intellij.platform.eel.impl.local.LocalEelArchiveApiImpl
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.Compressor
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

@TestApplication
class EelArchiveShowcase {

  @Test
  fun `extracts zip archive`(@TempDir dir: Path): Unit = runBlocking {
    val archive = dir.resolve("source.zip")
    Compressor.Zip(archive).use { zip ->
      zip.addFile("hello.txt", "hello".toByteArray())
      zip.addFile("nested/world.txt", "world".toByteArray())
    }
    val target = Files.createDirectory(dir.resolve("out"))

    LocalEelArchiveApiImpl.extract(archive.asEelPath(), target.asEelPath())

    assertFileContent(target.resolve("hello.txt"), "hello")
    assertFileContent(target.resolve("nested/world.txt"), "world")
  }

  @Test
  fun `extracts tar archive`(@TempDir dir: Path): Unit = runBlocking {
    val archive = dir.resolve("source.tar")
    Compressor.Tar(archive, Compressor.Tar.Compression.NONE).use { tar ->
      tar.addFile("a.txt", "alpha".toByteArray())
      tar.addFile("dir/b.txt", "beta".toByteArray())
    }
    val target = Files.createDirectory(dir.resolve("out"))

    LocalEelArchiveApiImpl.extract(archive.asEelPath(), target.asEelPath())

    assertFileContent(target.resolve("a.txt"), "alpha")
    assertFileContent(target.resolve("dir/b.txt"), "beta")
  }

  @Test
  fun `extracts tar gz archive`(@TempDir dir: Path): Unit = runBlocking {
    val archive = dir.resolve("source.tar.gz")
    Compressor.Tar(archive, Compressor.Tar.Compression.GZIP).use { tar ->
      tar.addFile("payload.txt", "gzipped".toByteArray())
    }
    val target = Files.createDirectory(dir.resolve("out"))

    LocalEelArchiveApiImpl.extract(archive.asEelPath(), target.asEelPath())

    assertFileContent(target.resolve("payload.txt"), "gzipped")
  }

  @Test
  fun `extracts tgz archive`(@TempDir dir: Path): Unit = runBlocking {
    val archive = dir.resolve("source.tgz")
    Compressor.Tar(archive, Compressor.Tar.Compression.GZIP).use { tar ->
      tar.addFile("inside.txt", "tgz".toByteArray())
    }
    val target = Files.createDirectory(dir.resolve("out"))

    LocalEelArchiveApiImpl.extract(archive.asEelPath(), target.asEelPath())

    assertFileContent(target.resolve("inside.txt"), "tgz")
  }

  @Test
  fun `extracts tar bz2 archive`(@TempDir dir: Path): Unit = runBlocking {
    val archive = dir.resolve("source.tar.bz2")
    Compressor.Tar(archive, Compressor.Tar.Compression.BZIP2).use { tar ->
      tar.addFile("payload.txt", "bzipped".toByteArray())
    }
    val target = Files.createDirectory(dir.resolve("out"))

    LocalEelArchiveApiImpl.extract(archive.asEelPath(), target.asEelPath())

    assertFileContent(target.resolve("payload.txt"), "bzipped")
  }

  @Test
  fun `extracts tbz2 archive`(@TempDir dir: Path): Unit = runBlocking {
    val archive = dir.resolve("source.tbz2")
    Compressor.Tar(archive, Compressor.Tar.Compression.BZIP2).use { tar ->
      tar.addFile("payload.txt", "tbz2".toByteArray())
    }
    val target = Files.createDirectory(dir.resolve("out"))

    LocalEelArchiveApiImpl.extract(archive.asEelPath(), target.asEelPath())

    assertFileContent(target.resolve("payload.txt"), "tbz2")
  }

  @Test
  fun `extracts tar xz archive`(@TempDir dir: Path): Unit = runBlocking {
    val archive = writeTarXz(dir.resolve("source.tar.xz"), payload = "xz")
    val target = Files.createDirectory(dir.resolve("out"))

    LocalEelArchiveApiImpl.extract(archive.asEelPath(), target.asEelPath())

    assertFileContent(target.resolve("payload.txt"), "xz")
  }

  @Test
  fun `extracts txz archive`(@TempDir dir: Path): Unit = runBlocking {
    val archive = writeTarXz(dir.resolve("source.txz"), payload = "txz")
    val target = Files.createDirectory(dir.resolve("out"))

    LocalEelArchiveApiImpl.extract(archive.asEelPath(), target.asEelPath())

    assertFileContent(target.resolve("payload.txt"), "txz")
  }

  @Test
  fun `preserves nested directory structure`(@TempDir dir: Path): Unit = runBlocking {
    val archive = dir.resolve("nested.zip")
    Compressor.Zip(archive).use { zip ->
      zip.addFile("level1/level2/level3/deep.txt", "deep".toByteArray())
      zip.addFile("level1/sibling.txt", "sibling".toByteArray())
    }
    val target = Files.createDirectory(dir.resolve("out"))

    LocalEelArchiveApiImpl.extract(archive.asEelPath(), target.asEelPath())

    assertFileContent(target.resolve("level1/level2/level3/deep.txt"), "deep")
    assertFileContent(target.resolve("level1/sibling.txt"), "sibling")
  }

  @Test
  fun `unsupported format throws IOException`(@TempDir dir: Path): Unit = runBlocking {
    val archive = dir.resolve("payload.rar")
    Files.write(archive, byteArrayOf(0, 1, 2))
    val target = Files.createDirectory(dir.resolve("out"))

    val thrown = runCatching { LocalEelArchiveApiImpl.extract(archive.asEelPath(), target.asEelPath()) }.exceptionOrNull()
    Assertions.assertTrue(thrown is IOException, "expected IOException, got $thrown")
  }

  @Test
  fun `missing archive throws IOException`(@TempDir dir: Path): Unit = runBlocking {
    val archive = dir.resolve("missing.zip")
    val target = Files.createDirectory(dir.resolve("out"))

    val thrown = runCatching { LocalEelArchiveApiImpl.extract(archive.asEelPath(), target.asEelPath()) }.exceptionOrNull()
    Assertions.assertTrue(thrown is IOException, "expected IOException, got $thrown")
  }

  private fun assertFileContent(path: Path, expected: String) {
    Assertions.assertTrue(Files.exists(path), "expected file $path")
    Assertions.assertEquals(expected, Files.readString(path))
  }

  private fun writeTarXz(target: Path, payload: String): Path {
    val rawTar = target.resolveSibling("${target.fileName}.raw.tar")
    Compressor.Tar(rawTar, Compressor.Tar.Compression.NONE).use { tar ->
      tar.addFile("payload.txt", payload.toByteArray())
    }
    target.outputStream().use { fileOut ->
      XZCompressorOutputStream(fileOut).use { xz ->
        rawTar.inputStream().use { it.copyTo(xz) }
      }
    }
    Files.delete(rawTar)
    return target
  }
}
