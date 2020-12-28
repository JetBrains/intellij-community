// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history.ngram

import com.intellij.completion.ngram.slp.modeling.Model
import com.intellij.completion.ngram.slp.translating.Vocabulary
import java.io.Externalizable
import java.io.IOException
import java.io.ObjectInput
import java.io.ObjectOutput
import java.util.concurrent.atomic.AtomicInteger

class FilePredictionNGramVocabulary(private var maxSize: Int) : Vocabulary(), Externalizable {
  private val counter: AtomicInteger = AtomicInteger(1)
  private val recent: ArrayList<String> = arrayListOf()

  fun toExistingIndices(token: List<String>): List<Int> {
    return token.mapNotNull { toExistingIndex(it) }
  }

  private fun toExistingIndex(token: String): Int? {
    return wordIndices[token] ?: wordIndices[unknownCharacter]
  }

  fun toIndicesWithLimit(token: List<String>, model: Model): List<Int> {
    return token.map { toIndexWithLimit(it, model) }
  }

  private fun toIndexWithLimit(token: String, model: Model): Int {
    var index: Int? = wordIndices[token]
    if (index == null) {
      index = counter.getAndIncrement()
      wordIndices[token] = index

      if (recent.size >= maxSize) {
        val toRemove = trimRecentTokensSize()
        model.forget(toExistingIndices(toRemove))
        for (tokenToRemove in toRemove) {
          wordIndices.remove(tokenToRemove)
        }
      }
    }

    recent.remove(token)
    recent.add(token)
    return index
  }

  private fun trimRecentTokensSize(): ArrayList<String> {
    val toRemove: ArrayList<String> = arrayListOf()
    while (recent.size >= maxSize) {
      val token = recent.removeAt(0)
      toRemove.add(token)
    }
    return toRemove
  }

  @Throws(IOException::class)
  override fun writeExternal(out: ObjectOutput) {
    out.writeInt(maxSize)
    out.writeInt(counter.get())
    out.writeInt(recent.size)
    for (recentTokenCode in recent) {
      out.writeObject(recentTokenCode)
    }

    out.writeInt(wordIndices.size)
    for ((token, code) in wordIndices) {
      out.writeObject(token)
      out.writeInt(code)
    }
  }

  @Throws(IOException::class)
  override fun readExternal(ins: ObjectInput) {
    maxSize = ins.readInt()
    counter.set(ins.readInt())

    val recentSize = ins.readInt()
    for (i in 0 until recentSize) {
      recent.add(ins.readObject() as String)
    }

    val wordsSize = ins.readInt()
    for (i in 0 until wordsSize) {
      val token = ins.readObject() as String
      val code = ins.readInt()
      wordIndices[token] = code
    }
  }
}
