// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.immutableKeyValueStore.benchmark

import org.jetbrains.ikv.builder.IkvWriter
import org.jetbrains.xxh3.Xxh3
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.random.Random

internal fun generateDb(file: Path, count: Int): List<Pair<Int, ByteArray>> {
  val random = Random(42)
  Files.createDirectories(file.parent)
  val list = ArrayList<Pair<Int, ByteArray>>(count)
  FileChannel.open(file, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)).use { channel ->
    val writer = IkvWriter(channel)
    writer.use {
      for (i in 0 until count) {
        val data = random.nextBytes(random.nextInt(64, 512))
        val key = Xxh3.hash(data).toInt()
        writer.write(writer.entry(key), data)
        list.add(Pair(key, data))
      }
    }
  }
  return list
}