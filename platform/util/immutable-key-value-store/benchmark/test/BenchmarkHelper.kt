// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.immutableKeyValueStore.benchmark

import com.dynatrace.hash4j.hashing.Hashing
import org.jetbrains.intellij.build.io.IkvWriter
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.random.Random

internal fun generateDb(file: Path, count: Int): List<Pair<Long, ByteArray>> {
  val random = Random(42)
  Files.createDirectories(file.parent)
  val list = ArrayList<Pair<Long, ByteArray>>(count)
  FileChannel.open(file, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)).use { channel ->
    val writer = IkvWriter(channel)
    writer.use {
      (0 until count).forEach { i ->
        val data = random.nextBytes(random.nextInt(64, 512))
        val key = Hashing.xxh3_64().hashBytesToLong(data)
        writer.write(writer.entry(key, data.size), data)
        list.add(Pair(key, data))
      }
    }
  }
  return list
}