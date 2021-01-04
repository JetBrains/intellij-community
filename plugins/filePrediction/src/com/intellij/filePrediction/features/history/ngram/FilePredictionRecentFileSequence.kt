// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history.ngram

import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel
import com.intellij.completion.ngram.slp.sequencing.NGramSequencer
import java.io.Externalizable
import java.io.IOException
import java.io.ObjectInput
import java.io.ObjectOutput
import kotlin.math.min

class FilePredictionRecentFileSequence(val maxSequenceLength: Int, private val nGramOrder: Int, private val initialSize: Int = INITIAL_SIZE) : Externalizable {
  companion object {
    private const val INITIAL_SIZE: Int = 100
  }

  private var fileSequence: IntArray = IntArray(initialSize) { Int.MAX_VALUE }
  private var start: Int = 0
  private var length: Int = 0

  internal fun addWithLimit(model: NGramModel, element: Int) {
    if (size() >= maxSequenceLength) {
      forget(model, subListFromStart(nGramOrder))
      adjustIndices(1)
    }
    add(element)
  }

  internal fun forgetUntil(model: NGramModel, keep: Int) {
    val forgetLast = size() - min(keep, maxSequenceLength)
    if (forgetLast > 0) {
      forget(model, subListFromStart(forgetLast + nGramOrder - 1))
      adjustIndices(forgetLast)
    }
  }

  private fun forget(model: NGramModel, indices: List<Int>) {
    val sequences = NGramSequencer.sequenceForward(indices, nGramOrder).filter { it.size == nGramOrder }
    model.counter.unCountBatch(sequences)
  }

  private fun add(element: Int) {
    val index = start + length
    while (fileSequence.size < maxSequenceLength && fileSequence.size <= index) {
      grow()
    }

    fileSequence[index % fileSequence.size] = element
    length++
  }

  private fun adjustIndices(remove: Int) {
    start = (start + remove) % fileSequence.size
    length -= remove
  }

  private fun elementAt(index: Int) = fileSequence[(start + index) % fileSequence.size]

  internal fun size() = length

  internal fun subListFromStart(newLength: Int): List<Int> {
    val size = fileSequence.size
    val end = start + newLength
    if (end <= size) {
      return fileSequence.copyOfRange(start, end).toList()
    }
    val first = fileSequence.copyOfRange(start, size)
    val second = fileSequence.copyOfRange(0, end % size)
    return (first + second).toList()
  }

  private fun grow() {
    val oldLen: Int = fileSequence.size
    val newLen = if (oldLen > 0) (oldLen * 2).coerceAtMost(maxSequenceLength) else initialSize
    fileSequence = fileSequence.copyOf(newLen)
    fileSequence.fill(Int.MAX_VALUE, oldLen)
  }

  @Throws(IOException::class)
  override fun writeExternal(out: ObjectOutput) {
    val size = size()
    out.writeInt(size)
    for (i in 0 until size) {
      val elementAt = elementAt(i)
      out.writeInt(elementAt)
    }
  }

  @Throws(IOException::class)
  override fun readExternal(ins: ObjectInput) {
    val size = ins.readInt()
    length = size
    fileSequence = IntArray(size)
    for (i in 0 until size) {
      fileSequence[i] = ins.readInt()
    }
  }
}