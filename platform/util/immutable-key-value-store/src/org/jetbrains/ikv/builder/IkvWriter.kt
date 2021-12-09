// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ikv.builder

import org.jetbrains.ikv.RecSplitSettings
import org.jetbrains.ikv.UniversalHash
import org.jetbrains.xxh3.Xxh3
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

fun sizeUnawareIkvWriter(file: Path): IkvWriter {
  return IkvWriter(channel = FileChannel.open(file, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)),
                   writeSize = false)
}

class IkvWriter(private val channel: FileChannel,
                settings: RecSplitSettings = RecSplitSettings.DEFAULT_SETTINGS,
                writeSize: Boolean = true) : AutoCloseable {
  private class Entry(@JvmField val key: Int, override val offset: Int, override val size: Int) : IkvIndexEntry {
    override fun equals(other: Any?) = key == (other as? Entry)?.key

    override fun hashCode() = key
  }

  private class EntryHash : UniversalHash<Entry> {
    override fun universalHash(key: Entry, index: Long) = Xxh3.hashInt(key.key, index)
  }

  private val indexBuilder = IkvIndexBuilder(hash = EntryHash(), writeSize = writeSize, settings = settings)
  private var position = 0

  fun write(key: Int, data: ByteArray) {
    indexBuilder.add(Entry(key, position, data.size))
    writeBuffer(ByteBuffer.wrap(data))
  }

  fun write(key: Int, data: ByteBuffer) {
    indexBuilder.add(Entry(key, position, data.remaining()))
    var currentPosition = position.toLong()
    do {
      currentPosition += channel.write(data, currentPosition)
    }
    while (data.hasRemaining())
    position = currentPosition.toInt()
  }

  override fun close() {
    channel.use {
      indexBuilder.write(::writeBuffer)
    }
  }

  private fun writeBuffer(value: ByteBuffer) {
    var currentPosition = position
    do {
      currentPosition += channel.write(value, currentPosition.toLong())
    }
    while (value.hasRemaining())
    position = currentPosition
  }
}
