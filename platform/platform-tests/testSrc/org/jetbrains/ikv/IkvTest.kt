// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ikv

import com.intellij.util.io.Murmur3_32Hash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.random.Random

class IkvTest {
  private val random = Random(42)

  @Test
  fun singleKey(@TempDir tempDir: Path) {
    val file = tempDir.resolve("db")

    val data = random.nextBytes(random.nextInt(64, 512))
    val key = Murmur3_32Hash.MURMUR3_32.hashBytes(data, 0, data.size)

    Files.createDirectories(file.parent)
    FileChannel.open(file, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)).use { channel ->
      val writer = IkvWriter(channel)
      writer.use {
        writer.write(key, data)
      }
    }

    Ikv.loadSizeAwareIkv(file).use {
      assertThat(it.getValue(key)).isEqualTo(ByteBuffer.wrap(data))
    }
  }

  @Test
  fun singleKeySizeUnaware(@TempDir tempDir: Path) {
    val file = tempDir.resolve("db")

    val data = random.nextBytes(random.nextInt(64, 512))
    val key = Murmur3_32Hash.MURMUR3_32.hashBytes(data, 0, data.size)

    Files.createDirectories(file.parent)
    FileChannel.open(file, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)).use { channel ->
      val writer = IkvWriter(channel, writeSize = false)
      writer.use {
        writer.write(key, data)
      }
    }

    Ikv.loadSizeUnawareIkv(file).use {
      val value = it.getUnboundedValue(key)
      assertThat(value).isNotEqualTo(ByteBuffer.wrap(data))
      assertThat(value.slice().limit(value.position() + data.size)).isEqualTo(ByteBuffer.wrap(data))
    }
  }

  @Test
  fun `two keys`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("db")

    val list = generateDb(file, 2)
    Ikv.loadSizeAwareIkv(file).use {
      for ((key, data) in list) {
        assertThat(it.getValue(key)).isEqualTo(ByteBuffer.wrap(data))
      }
    }
  }

  @Test
  fun manyKeys(@TempDir tempDir: Path) {
    val file = tempDir.resolve("db")

    val list = generateDb(file, 1_024)
    Ikv.loadSizeAwareIkv(file).use { ikv ->
      for ((key, data) in list) {
        assertThat(ikv.getValue(key)).isEqualTo(ByteBuffer.wrap(data))
      }
    }
  }

  private fun generateDb(file: Path, count: Int): List<Pair<Int, ByteArray>> {
    Files.createDirectories(file.parent)
    val list = ArrayList<Pair<Int, ByteArray>>(count)
    FileChannel.open(file, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)).use { channel ->
      val writer = IkvWriter(channel)
      writer.use {
        for (i in 0 until count) {
          val data = random.nextBytes(random.nextInt(64, 512))
          val key = Murmur3_32Hash.MURMUR3_32.hashBytes(data, 0, data.size)
          writer.write(key, data)
          list.add(Pair(key, data))
        }
      }
    }
    return list
  }
}