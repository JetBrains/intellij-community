// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ikv

import org.jetbrains.ikv.IkvIndexBuilder.Entry
import org.minperf.RecSplitBuilder
import org.minperf.universal.LongHash
import org.minperf.universal.UniversalHash
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class IkvIndexBuilder(private val writeSize: Boolean = true) {
  private val entries = ArrayList<Entry>()

  data class Entry(val key: Int, val offset: Int, val size: Int)

  private class EntryHash : UniversalHash<Entry> {
    override fun universalHash(key: Entry, index: Long): Long {
      return LongHash.universalHash(key.key.toLong(), index)
    }
  }

  fun add(entry: Entry) {
    entries.add(entry)
  }

  fun write(writer: (ByteBuffer) -> Unit) {
    val builder = RecSplitBuilder.newInstance(EntryHash())
    val keyData = builder.generate(entries)
    keyData.seek(0)

    val buffer = ByteBuffer
      .allocateDirect(keyData.serializedSize +
                      (entries.size * (if (writeSize) Long.SIZE_BYTES else Int.SIZE_BYTES)) +
                      (Int.SIZE_BYTES * 2) + 1)
      .order(ByteOrder.LITTLE_ENDIAN)
    try {
      // write key data
      keyData.writeTo(buffer)

      // write offsets in key index order
      val evaluator = builder.buildEvaluator(keyData)
      entries.sortWith(Comparator { o1, o2 -> evaluator.evaluate(o1).compareTo(evaluator.evaluate(o2)) })

      if (writeSize) {
        val longBuffer = buffer.asLongBuffer()
        for (entry in entries) {
          longBuffer.put(entry.offset.toLong() shl 32 or (entry.size.toLong() and 0xffffffffL))
        }
        buffer.position(buffer.position() + (longBuffer.position() * Long.SIZE_BYTES))
      }
      else {
        val intBuffer = buffer.asIntBuffer()
        for (entry in entries) {
          intBuffer.put(entry.offset)
        }
        buffer.position(buffer.position() + (intBuffer.position() * Int.SIZE_BYTES))
      }

      buffer.putInt(entries.size)
      buffer.putInt(keyData.serializedSize)
      buffer.put(if (writeSize) 1 else 0)
      buffer.flip()
      writer(buffer)
    }
    finally {
      Ikv.unmapBuffer(buffer)
    }
  }
}

class IkvWriter(private val channel: FileChannel, writeSize: Boolean = true) : AutoCloseable {
  private val indexBuilder = IkvIndexBuilder(writeSize)
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
