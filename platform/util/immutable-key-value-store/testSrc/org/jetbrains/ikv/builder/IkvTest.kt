// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ikv.builder

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.ikv.Ikv
import org.jetbrains.ikv.UniversalHash
import org.jetbrains.xxh3.Xxh3
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.random.Random

internal class IkvTest {
  private val random = Random(42)

  companion object {
    private fun generateDb(file: Path, count: Int, random: Random): List<Pair<Int, ByteArray>> {
      Files.createDirectories(file.parent)
      val list = ArrayList<Pair<Int, ByteArray>>(count)
      FileChannel.open(file, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)).use { channel ->
        val writer = IkvWriter(channel)
        writer.use {
          for (i in 0 until count) {
            val data = random.nextBytes(random.nextInt(64, 512))
            val key = Xxh3.hash32(data)
            writer.write(key, data)
            list.add(Pair(key, data))
          }
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
    val key = Xxh3.hash32(data)

    Files.createDirectories(file.parent)
    FileChannel.open(file, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)).use { channel ->
      val writer = IkvWriter(channel)
      writer.use {
        writer.write(key, data)
      }
    }

    Ikv.loadSizeAwareIkv(file, UniversalHash.IntHash()).use {
      assertThat(it.getValue(key)).isEqualTo(ByteBuffer.wrap(data))
    }
  }

  @Test
  fun singleKeySizeUnaware() {
    val file = tempDir!!.resolve("db")

    val data = random.nextBytes(random.nextInt(64, 512))
    val key = Xxh3.hash32(data)

    Files.createDirectories(file.parent)
    FileChannel.open(file, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)).use { channel ->
      val writer = IkvWriter(channel, writeSize = false)
      writer.use {
        writer.write(key, data)
      }
    }

    Ikv.loadSizeUnawareIkv(file, UniversalHash.IntHash()).use {
      val value = it.getUnboundedValue(key)
      assertThat(value).isNotEqualTo(ByteBuffer.wrap(data))
      assertThat(value.slice().limit(value.position() + data.size)).isEqualTo(ByteBuffer.wrap(data))
    }
  }

  @Test
  fun `two keys`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("db")

    val list = generateDb(file, 2, random)
    Ikv.loadSizeAwareIkv(file, UniversalHash.IntHash()).use {
      for ((key, data) in list) {
        assertThat(it.getValue(key)).isEqualTo(ByteBuffer.wrap(data))
      }
    }
  }

  @ParameterizedTest
  @ValueSource(ints = [10, 16, 32, 64, 128, 200, 1000, 1_024, 2_048, 5000, 10_000])
  fun manyKeys(keyCount: Int) {
    val file = tempDir!!.resolve("db")

    val list = generateDb(file, keyCount, random)
    Ikv.loadSizeAwareIkv(file, UniversalHash.IntHash()).use { ikv ->
      for ((key, data) in list) {
        assertThat(ikv.getValue(key)).isEqualTo(ByteBuffer.wrap(data))
      }
    }
  }
}