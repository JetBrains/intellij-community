package com.intellij.grazie.jlanguage.chunkers

import org.apache.commons.lang3.StringUtils
import org.languagetool.AnalyzedTokenReadings
import org.languagetool.chunking.ChunkTag
import org.languagetool.chunking.Chunker
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * English phrase chunker based on <a href="https://github.com/clips/pattern">https://github.com/clips/pattern</a> implementation
 */
class GrazieEnglishChunker : Chunker {
  private val SEPARATOR = "/"

  private val NN = "NN|NNS|NNP|NNPS|NNPS?\\-[A-Z]{3,4}|PR|PRP|PRP\\$"
  private val VB = "VB|VBD|VBG|VBN|VBP|VBZ"
  private val JJ = "JJ|JJR|JJS"
  private val RB = "(?<!W)RB|RBR|RBS"

  private val CHUNKS = ArrayList<Pair<String, Pattern>>().apply {
    fun load(key: String, pattern: String) {
      add(key to Pattern.compile(pattern
                                   .replace("NN", NN)
                                   .replace("VB", VB)
                                   .replace("JJ", JJ)
                                   .replace("RB", RB)
                                   .replace(" ", "")
      ))
    }

    load("NP", "((NN)/)* ((DT|CD|CC)/((VBN)/)*)* ((RB|JJ)/((VBN)/)*)* (((JJ)/(CC|,)/)*(JJ)/)* ((NN)/)+ ((CD)/)*")
    load("ADJP", "((RB|JJ)/)* ((JJ)/,/)* ((JJ)/(CC)/)* ((JJ)/)+")
    load("VP", "(((MD|TO)/)* ((VB)/)+ ((RP)/)*)+")
    load("VP", "((MD)/)")
    load("PP", "((IN|PP)/)+")
    load("ADVP", "((RB)/)+")
  }

  fun chunk(tokens: List<String>, posTags: List<String>): Array<String> {
    val chunks = arrayOfNulls<String>(tokens.size)
    val tags: String = posTags.joinToString(SEPARATOR, postfix = SEPARATOR)
    for ((tag, rule) in CHUNKS) {
      val matcher: Matcher = rule.matcher(tags)
      while (matcher.find()) {
        // Find the start of chunks inside the tags-string
        // Number of preceding separators = number of preceding tokens
        val i: Int = matcher.start()
        var j: Int = StringUtils.countMatches(tags.substring(0, i), SEPARATOR)
        val n: Int = StringUtils.countMatches(matcher.group(), SEPARATOR)

        val cond = j + n
        for (k in j until cond) {
          if (chunks[k] != null) continue
          // A conjunction or comma cannot be start of a chunk
          if (k == j && (posTags[k] == "CC" || posTags[k] == ",")) {
            j += 1
          }
          // Mark first token in chunk with B-
          else if (k == j) {
            chunks[k] = "B-$tag"
          }
          // Mark other tokens in chunk with I-
          else {
            chunks[k] = "I-$tag"
          }
        }
      }
    }

    // Mark chinks (tokens outside of a chunk) with O-
    for (i in chunks.indices) {
      if (chunks[i] == null) {
        chunks[i] = "O"
      }
    }
    // Post-processing corrections
    for (i in chunks.indices) {
      if (posTags[i].startsWith("RB") && chunks[i] == "B-NP") {
        if (i < chunks.size - 1 && !posTags[i + 1].startsWith("JJ")) {
          chunks[i] = "B-ADVP"
          chunks[i + 1] = "B-NP"
        }

        if (i < chunks.size - 1 && (posTags[i + 1] == "CC" || posTags[i + 1] == ",")) {
          chunks[i + 1] = "O"
        }

        if (i < chunks.size - 2 && chunks[i + 1] == "O") {
          chunks[i + 2] = "B-NP"
        }
      }
    }

    @Suppress("UNCHECKED_CAST")
    return chunks as Array<String>
  }

  override fun addChunkTags(readings: MutableList<AnalyzedTokenReadings>) {
    val real = readings.filter {
      it.readings.size > 0 &&
      it.readings.first().posTag != null &&
      it.readings.first().token.isNotBlank() &&
      it.readings.first().token != "'"
    }

    val tokens = real.map { it.readings.first().token!! }
    val tags = real.map { it.readings.first().posTag!! }

    chunk(tokens, tags).zip(real) { chunk, reading ->
      reading.chunkTags = listOf(ChunkTag(chunk))
    }
  }
}
