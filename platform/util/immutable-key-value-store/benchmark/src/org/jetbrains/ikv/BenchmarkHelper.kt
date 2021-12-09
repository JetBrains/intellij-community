// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ikv

import org.jetbrains.ikv.builder.IkvWriter
import org.jetbrains.xxh3.Xxh3
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.random.Random

internal fun generateDb(file: Path, count: Int, settings: RecSplitSettings): List<Pair<Int, ByteArray>> {
  val random = Random(42)
  Files.createDirectories(file.parent)
  val list = ArrayList<Pair<Int, ByteArray>>(count)
  FileChannel.open(file, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)).use { channel ->
    val writer = IkvWriter(channel, settings)
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