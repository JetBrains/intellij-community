// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history.ngram

import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel
import com.intellij.completion.ngram.slp.sequencing.NGramSequencer
import java.io.Externalizable
import java.io.IOException
import java.io.ObjectInput
import java.io.ObjectOutput
import kotlin.math.min

class FilePredictionRecentFileSequence(val maxSequenceLength: Int, private val nGramOrder: Int) : Externalizable {
  private val fileSequence: MutableList<Int> = arrayListOf()
  private var start: Int = 0

  internal fun addWithLimit(model: NGramModel, index: Int) {
    fileSequence.add(index)

    if (size() > maxSequenceLength) {
      forget(model, subListFromStart(nGramOrder))
      start++
    }
  }

  internal fun forgetUntil(model: NGramModel, keep: Int) {
    val forgetLast = size() - min(keep, maxSequenceLength)
    if (forgetLast > 0) {
      forget(model, subListFromStart(forgetLast + nGramOrder - 1))
      start += forgetLast
    }
  }

  private fun forget(model: NGramModel, indices: List<Int>) {
    val sequences = NGramSequencer.sequenceForward(indices, nGramOrder).filter { it.size == nGramOrder }
    model.counter.unCountBatch(sequences)
  }

  private fun elementAt(index: Int) = fileSequence[start + index]

  internal fun size() = fileSequence.size - start

  internal fun subListFromStart(length: Int): List<Int> {
    return fileSequence.subList(start, (start + length).coerceAtMost(fileSequence.size))
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
    for (i in 0 until size) {
      fileSequence.add(ins.readInt())
    }
  }
}