// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history.ngram

import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel
import com.intellij.completion.ngram.slp.translating.Vocabulary
import com.intellij.openapi.diagnostic.Logger
import java.io.Externalizable
import java.io.IOException
import java.io.ObjectInput
import java.io.ObjectOutput
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

class FilePredictionNGramVocabulary(var maxSize: Int,
                                    val fileSequence: FilePredictionRecentFileSequence) : Vocabulary(), Externalizable {

  companion object {
    internal val LOG: Logger = Logger.getInstance(FilePredictionNGramVocabulary::class.java)
  }

  private val counter: AtomicInteger = AtomicInteger(1)
  internal val recent: FilePredictionNGramRecentFiles = FilePredictionNGramRecentFiles()

  fun toExistingIndices(token: List<String>): List<Int> {
    return token.mapNotNull { toExistingIndex(it) }
  }

  private fun toExistingIndex(token: String): Int? {
    return wordIndices[token] ?: wordIndices[unknownCharacter]
  }

  fun toIndicesWithLimit(token: List<String>, model: NGramModel): List<Int> {
    val indices = token.map { toIndexWithLimit(it, model) }

    fileSequence.addWithLimit(model, indices.last())
    updateRecentFiles(token)
    return indices
  }

  private fun toIndexWithLimit(token: String, model: NGramModel): Int {
    var index: Int? = wordIndices[token]
    if (index == null) {
      index = counter.getAndIncrement()
      wordIndices[token] = index

      if (recent.size() >= maxSize) {
        val (toRemove, latestAppearance) = trimRecentTokensSize()
        fileSequence.forgetUntil(model, if (recent.size() > 0) recent.lastIndex() - latestAppearance else 0)
        for (tokenToRemove in toRemove) {
          wordIndices.remove(tokenToRemove)
        }
      }
    }
    return index
  }

  private fun trimRecentTokensSize(): Pair<ArrayList<String>, Int> {
    val toRemove: ArrayList<String> = arrayListOf()
    var latestAppearance = 0
    while (recent.size() >= maxSize) {
      val (token, idx) = recent.removeAt(0)
      latestAppearance = max(latestAppearance, idx)
      toRemove.add(token)
    }
    return toRemove to latestAppearance
  }

  private fun updateRecentFiles(tokens: List<String>) {
    if (LOG.isDebugEnabled) {
      assertUpdateIsIncremental(tokens)
    }

    if (tokens.isNotEmpty()) {
      val newToken = tokens.last()
      updateRecent(recent.lastIndexOf(tokens.last()), newToken)
    }
  }

  private fun updateRecent(oldIndex: Int, token: String) {
    if (oldIndex >= 0) {
      recent.removeAt(oldIndex)
    }
    recent.add(token)
  }

  private fun assertUpdateIsIncremental(tokens: List<String>) {
    for (i in 0..tokens.size - 2) {
      val oldIndex = recent.lastIndexOf(tokens[i])
      if (oldIndex < recent.size() - tokens.size + i + 1 || oldIndex > recent.size() - 1) {
        LOG.error("Cannot find previous token in recent files: ${tokens[i]} in $tokens")
      }
    }
  }

  @Throws(IOException::class)
  override fun writeExternal(out: ObjectOutput) {
    out.writeInt(maxSize)
    out.writeInt(counter.get())
    recent.writeExternal(out)
    fileSequence.writeExternal(out)

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
    recent.readExternal(ins)
    fileSequence.readExternal(ins)

    val wordsSize = ins.readInt()
    for (i in 0 until wordsSize) {
      val token = ins.readObject() as String
      val code = ins.readInt()
      wordIndices[token] = code
    }
  }
}

internal class FilePredictionNGramRecentFiles : Externalizable {
  internal val nextFileSequenceIdx: AtomicInteger = AtomicInteger(1)
  internal val recent: ArrayList<String> = arrayListOf()
  internal val recentIdx: ArrayList<Int> = arrayListOf()

  @Synchronized
  fun add(token: String) {
    assertStateConsistent()

    recent.add(token)
    recentIdx.add(nextFileSequenceIdx.getAndIncrement())
  }

  @Synchronized
  fun removeAt(index: Int): Pair<String, Int> {
    assertStateConsistent()
    val id = recentIdx.removeAt(index)
    val token = recent.removeAt(index)
    return token to id
  }

  fun lastIndexOf(token: String): Int {
    assertStateConsistent()
    return recent.lastIndexOf(token)
  }

  fun lastIndex(): Int {
    assertStateConsistent()
    return recentIdx.last()
  }

  fun size(): Int {
    assertStateConsistent()
    return recent.size
  }

  private fun assertStateConsistent() {
    if (FilePredictionNGramVocabulary.LOG.isDebugEnabled && recent.size != recentIdx.size) {
      FilePredictionNGramVocabulary.LOG.error(
        "Number of recent files should be equal to number of recent ids: ${recent.size} vs. ${recentIdx.size} ($recent vs. $recentIdx)"
      )
    }
  }

  @Throws(IOException::class)
  override fun writeExternal(out: ObjectOutput) {
    out.writeInt(nextFileSequenceIdx.get())
    out.writeInt(recent.size)
    for (i in 0 until recent.size) {
      out.writeObject(recent[i])
      out.writeInt(recentIdx[i])
    }
  }

  @Throws(IOException::class)
  override fun readExternal(ins: ObjectInput) {
    nextFileSequenceIdx.set(ins.readInt())

    val recentSize = ins.readInt()
    for (i in 0 until recentSize) {
      recent.add(ins.readObject() as String)
      recentIdx.add(ins.readInt())
    }
  }
}