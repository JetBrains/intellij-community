// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ikv.builder

import com.intellij.util.lang.ByteBufferCleaner
import org.jetbrains.ikv.RecSplitEvaluator
import org.jetbrains.ikv.RecSplitSettings
import org.jetbrains.ikv.UniversalHash
import java.nio.ByteBuffer
import java.nio.ByteOrder

// must be in a separate package to make sure that it will be not loaded via app class loader if test started using unified class loader

class IkvIndexBuilder<T : IkvIndexEntry>(private val hash: UniversalHash<T>,
                                         private val writeSize: Boolean = true,
                                         private val settings: RecSplitSettings = RecSplitSettings.DEFAULT_SETTINGS) {
  private val entries = LinkedHashSet<T>()

  fun add(entry: T) {
    if (!entries.add(entry)) {
      throw IllegalStateException("$entry duplicates ${entries.find { it == entry }}\n")
    }
  }

  fun write(writer: (ByteBuffer) -> Unit): List<T> {
    return writeIkvIndex(unsortedEntries = entries, hash = hash, settings = settings, writeSize = writeSize, writer = writer)
  }
}

interface IkvIndexEntry {
  val size: Int
  val offset: Int
}

private fun <T : IkvIndexEntry> writeIkvIndex(unsortedEntries: Collection<T>,
                                              hash: UniversalHash<T>,
                                              settings: RecSplitSettings = RecSplitSettings.DEFAULT_SETTINGS,
                                              writeSize: Boolean = true,
                                              writer: (ByteBuffer) -> Unit): List<T> {
  val keyData = RecSplitGenerator(hash, settings).generate(unsortedEntries) {
    ByteBuffer.allocateDirect(it).order(ByteOrder.LITTLE_ENDIAN)
  }

  try {
    writer(keyData)
    keyData.flip()

    val buffer = ByteBuffer.allocateDirect((unsortedEntries.size * (if (writeSize) Long.SIZE_BYTES else Int.SIZE_BYTES)) +
                                           (Int.SIZE_BYTES * 2) + 1)
      .order(ByteOrder.LITTLE_ENDIAN)
    try {
      // write offsets in key index order
      val evaluator = RecSplitEvaluator(keyData, hash, settings)
      keyData.flip()
      val sortedEntries = unsortedEntries.sortedWith(Comparator { o1, o2 -> evaluator.evaluate(o1).compareTo(evaluator.evaluate(o2)) })

      if (writeSize) {
        val longBuffer = buffer.asLongBuffer()
        for (entry in sortedEntries) {
          longBuffer.put(entry.offset.toLong() shl 32 or (entry.size.toLong() and 0xffffffffL))
        }
        buffer.position(buffer.position() + (longBuffer.position() * Long.SIZE_BYTES))
      }
      else {
        val intBuffer = buffer.asIntBuffer()
        for (entry in sortedEntries) {
          intBuffer.put(entry.offset)
        }
        buffer.position(buffer.position() + (intBuffer.position() * Int.SIZE_BYTES))
      }

      buffer.putInt(sortedEntries.size)
      buffer.putInt(keyData.remaining())
      buffer.put(if (writeSize) 1 else 0)
      buffer.flip()
      writer(buffer)
      return sortedEntries
    }
    finally {
      ByteBufferCleaner.unmapBuffer(buffer)
    }
  }
  finally {
    ByteBufferCleaner.unmapBuffer(keyData)
  }
}