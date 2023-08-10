// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ikv.builder

import com.intellij.util.lang.ByteBufferCleaner
import java.nio.ByteBuffer
import java.nio.ByteOrder

// must be in a separate package to make sure that it will be not loaded via app class loader if test started using unified class loader

class IkvIndexBuilder(private val writeSize: Boolean = true) {
  private val entries = LinkedHashSet<IkvIndexEntry>()

  fun entry(key: Long, offset: Long, size: Int): IkvIndexEntry {
    val entry = LongKeyedEntry(longKey = key, offset = offset)
    entry.size = size
    return entry
  }

  fun add(entry: IkvIndexEntry) {
    if (!entries.add(entry)) {
      throw IllegalStateException("$entry duplicates ${entries.find { it == entry }}\n")
    }
  }

  fun write(writer: (ByteBuffer) -> Unit) {
    writeIkvIndex(entries = entries, writeSize = writeSize, writer = writer)
  }
}

sealed class IkvIndexEntry(@JvmField internal val offset: Long) {
  internal var size: Int = -1
}

internal class IntKeyedEntry(@JvmField internal val intKey: Int, offset: Long) : IkvIndexEntry(offset) {
  override fun equals(other: Any?): Boolean = intKey == (other as? IntKeyedEntry)?.intKey

  override fun hashCode(): Int = intKey
}

internal class LongKeyedEntry(@JvmField internal val longKey: Long, offset: Long) : IkvIndexEntry(offset) {
  override fun equals(other: Any?): Boolean = longKey == (other as? LongKeyedEntry)?.longKey

  override fun hashCode(): Int = longKey.toInt()
}

private fun writeIkvIndex(entries: Collection<IkvIndexEntry>, writeSize: Boolean = true, writer: (ByteBuffer) -> Unit) {
  val keyListSize = entries.size * (if (writeSize) Long.SIZE_BYTES else Int.SIZE_BYTES)
  val buffer = ByteBuffer.allocateDirect(keyListSize +
                                         (entries.size * (if (writeSize) Long.SIZE_BYTES else Int.SIZE_BYTES)) +
                                         Int.SIZE_BYTES + 1)
    .order(ByteOrder.LITTLE_ENDIAN)
  try {
    if (writeSize) {
      val longBuffer = buffer.asLongBuffer()
      if (entries.firstOrNull() is IntKeyedEntry) {
        for (entry in entries) {
          longBuffer.put((entry as IntKeyedEntry).intKey.toLong())
          longBuffer.put(entry.offset shl 32 or (entry.size.toLong() and 0xffffffffL))
        }
      }
      else {
        for (entry in entries) {
          longBuffer.put((entry as LongKeyedEntry).longKey)
          longBuffer.put(entry.offset shl 32 or (entry.size.toLong() and 0xffffffffL))
        }
      }
      buffer.position(buffer.position() + (longBuffer.position() * Long.SIZE_BYTES))
    }
    else {
      val intBuffer = buffer.asIntBuffer()
      for (entry in entries) {
        intBuffer.put((entry as IntKeyedEntry).intKey)
        intBuffer.put(Math.toIntExact(entry.offset))
      }
      buffer.position(buffer.position() + (intBuffer.position() * Int.SIZE_BYTES))
    }

    buffer.putInt(entries.size)
    buffer.put(if (writeSize) 1 else 0)
    buffer.flip()
    writer(buffer)
  }
  finally {
    ByteBufferCleaner.unmapBuffer(buffer)
  }
}