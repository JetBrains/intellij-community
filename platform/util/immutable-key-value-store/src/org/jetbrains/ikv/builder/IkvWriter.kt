// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ikv.builder

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

fun sizeUnawareIkvWriter(file: Path): IkvWriter {
  return IkvWriter(channel = FileChannel.open(file, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)), writeSize = false)
}

fun sizeAwareIkvWriter(file: Path): IkvWriter {
  return IkvWriter(channel = FileChannel.open(file, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)), writeSize = true)
}

class IkvWriter(private val channel: FileChannel, writeSize: Boolean = true) : AutoCloseable {
  private val indexBuilder = IkvIndexBuilder(writeSize)
  private var position = 0L

  private var lastEntry: IkvIndexEntry? = null

  fun entry(key: Int): IkvIndexEntry = IntKeyedEntry(intKey = key, offset = position)

  fun entry(key: Long): IkvIndexEntry = LongKeyedEntry(longKey = key, offset = position)

  fun write(entry: IkvIndexEntry, data: ByteArray) {
    writeBuffer(ByteBuffer.wrap(data))
    addEntry(entry)
  }

  fun write(entry: IkvIndexEntry, data: ByteBuffer) {
    var currentPosition = position
    do {
      currentPosition += channel.write(data, currentPosition)
    }
    while (data.hasRemaining())
    position = currentPosition
    addEntry(entry)
  }

  fun write(entry: IkvIndexEntry, writer: (FileChannel, position: Long) -> Long) {
    position = writer(channel, position)
    addEntry(entry)
  }

  private fun addEntry(entry: IkvIndexEntry) {
    indexBuilder.add(entry)
    entry.size = Math.toIntExact(position - entry.offset)
  }

  @Suppress("DuplicatedCode")
  override fun close() {
    channel.use {
      lastEntry?.let {
        it.size = Math.toIntExact(position - it.offset)
      }
      indexBuilder.write(::writeBuffer)
    }
  }

  private fun writeBuffer(value: ByteBuffer) {
    var currentPosition = position
    do {
      currentPosition += channel.write(value, currentPosition)
    }
    while (value.hasRemaining())
    position = currentPosition
  }
}
