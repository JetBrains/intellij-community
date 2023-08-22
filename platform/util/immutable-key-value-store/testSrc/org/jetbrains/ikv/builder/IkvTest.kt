// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ikv.builder

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.ikv.Ikv
import org.jetbrains.xxh3.Xxh3
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

internal class IkvTest {
  private val random = Random(42)

  companion object {
    private fun generateDb(file: Path, count: Int, random: Random): List<Pair<Int, ByteArray>> {
      Files.createDirectories(file.parent)
      val list = ArrayList<Pair<Int, ByteArray>>(count)
      sizeAwareIkvWriter(file).use { writer ->
        for (i in 0 until count) {
          val data = random.nextBytes(random.nextInt(64, 512))
          val key = Xxh3.hash(data).toInt()
          writer.write(writer.entry(key), data)
          list.add(Pair(key, data))
        }
      }
      return list
    }
  }

  @TempDir
  @JvmField
  var tempDir: Path? = null

  @Test
  fun singleKey() {
    val file = tempDir!!.resolve("db")

    val data = random.nextBytes(random.nextInt(64, 512))
    val key = Xxh3.hash(data).toInt()

    Files.createDirectories(file.parent)
    sizeAwareIkvWriter(file).use { writer ->
      writer.write(writer.entry(key), data)
    }

    Ikv.loadSizeAwareIkv(file).use {
      assertThat(it.getValue(key.toLong())).isEqualTo(ByteBuffer.wrap(data))
    }
  }

  @Test
  fun singleKeySizeUnaware() {
    val file = tempDir!!.resolve("db")

    val data = random.nextBytes(random.nextInt(64, 512))
    val key = Xxh3.hash(data).toInt()

    Files.createDirectories(file.parent)
    sizeUnawareIkvWriter(file).use { writer ->
      writer.write(writer.entry(key), data)
    }

    Ikv.loadSizeUnawareIkv(file).use {
      val value = it.getUnboundedValue(key)
      assertThat(value).isNotEqualTo(ByteBuffer.wrap(data))
      assertThat(value.slice().limit(value.position() + data.size)).isEqualTo(ByteBuffer.wrap(data))
    }
  }

  //@Test
  //fun app() {
  //  val file = Path.of("/Users/develar/Desktop/app.jar")
  //  val zip = ImmutableZipFile.load(file)
  //  val time = measureTimeMillis {
  //    repeat(100_00) {
  //      val byteBuffer = zip.getByteBuffer("ai/grazie/DataHolder.class")
  //      if (byteBuffer!!.position() < 0) {
  //        println(12)
  //      }
  //    }
  //  }
  //  println(time)
  //}

  @Test
  fun `two keys`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("db")

    val list = generateDb(file = file, count = 2, random = random)
    Ikv.loadSizeAwareIkv(file).use {
      for ((key, data) in list) {
        assertThat(it.getValue(key.toLong())).isEqualTo(ByteBuffer.wrap(data))
      }
    }
  }

  @ParameterizedTest
  @ValueSource(ints = [10, 16, 32, 64, 128, 200, 1000, 1_024, 2_048, 5000, 10_000])
  fun manyKeys(keyCount: Int) {
    val file = tempDir!!.resolve("db")

    val list = generateDb(file = file, count = keyCount, random = random)
    Ikv.loadSizeAwareIkv(file).use { ikv ->
      for ((key, data) in list) {
        assertThat(ikv.getValue(key.toLong())).isEqualTo(ByteBuffer.wrap(data))
      }
    }
  }
}