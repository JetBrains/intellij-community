package com.intellij.grazie.markdown.specification.search

import com.intellij.lang.cacheBuilder.SimpleWordsScanner
import org.jetbrains.annotations.ApiStatus
import kotlin.math.ln
import kotlin.math.sqrt

/** Finds Markdown documents that are similar to each other. */
@ApiStatus.Internal
object SpecificationFileSelector {
  private const val MIN_TOKEN_LENGTH = 3
  private const val MIN_SCORE = 0.1
  internal const val MAX_FILES_PER_SEARCH_TERM = 200

  /** A document keeps only its longest terms. Short words are usually common in every language. */
  internal const val MAX_TERMS_PER_DOCUMENT = 50

  /** An indexed Markdown document without its text. */
  @ApiStatus.Internal
  data class IndexedDocument(val path: String, val terms: Map<String, Int>)

  fun index(path: String, text: String): IndexedDocument = IndexedDocument(path, buildTermFrequencies(text))

  /**
   * Returns the [candidates] that are similar to [root], sorted by path.
   * The score is symmetric, so [root] is also similar to each returned document.
   */
  fun findSimilar(
    root: IndexedDocument,
    candidates: Collection<IndexedDocument>,
    documentFrequencies: Map<String, Int>,
  ): List<IndexedDocument> {
    val tfIdf = TfIdf(documentFrequencies)
    return candidates
      .asSequence()
      .filter { it.path != root.path }
      .distinctBy { it.path }
      .filter { tfIdf.score(root, it) >= MIN_SCORE }
      .sortedBy { it.path }
      .toList()
  }

  private class TfIdf(private val documentFrequencies: Map<String, Int>) {

    fun score(first: IndexedDocument, second: IndexedDocument): Double {
      val sharedRareTerms = first.terms.keys
        .asSequence()
        .filter {
          it in second.terms && documentFrequencies[it]?.let { frequency -> frequency in 1..MAX_FILES_PER_SEARCH_TERM } == true
        }
        .toSet()
      if (sharedRareTerms.isEmpty()) return 0.0

      val firstVector = vector(first.terms, sharedRareTerms)
      val secondVector = vector(second.terms, sharedRareTerms)
      val firstLength = documentLength(first)
      val secondLength = documentLength(second)
      return if (firstLength == 0.0 || secondLength == 0.0) 0.0
      else dot(firstVector, secondVector) / (firstLength * secondLength)
    }

    private fun vector(frequencies: Map<String, Int>, terms: Set<String>): Map<String, Double> =
      terms.associateWith { term ->
        (1.0 + ln(frequencies.getValue(term).toDouble())) * inverseDocumentFrequency(term)
      }

    private fun inverseDocumentFrequency(term: String): Double {
      val documentFrequency = documentFrequencies.getValue(term)
      return ln(1.0 + MAX_FILES_PER_SEARCH_TERM.toDouble() / documentFrequency) /
             ln(1.0 + MAX_FILES_PER_SEARCH_TERM)
    }

    private fun documentLength(document: IndexedDocument): Double = sqrt(document.terms.values.sum().toDouble())

    private fun dot(first: Map<String, Double>, second: Map<String, Double>): Double =
      first.entries.sumOf { (term, weight) -> weight * (second[term] ?: 0.0) }
  }

  /**
   * Splits [text] into words with the scanner of the word index.
   * Then a text search for a term finds every document that has the term, so the similarity is symmetric.
   * Only the [MAX_TERMS_PER_DOCUMENT] longest terms remain. The score uses the terms of both documents, so it stays symmetric.
   */
  internal fun buildTermFrequencies(text: String): Map<String, Int> {
    val frequencies = HashMap<String, Int>()
    SimpleWordsScanner().processWords(text) { occurrence ->
      val term = occurrence.baseText.subSequence(occurrence.start, occurrence.end).toString().lowercase()
      if (term.length >= MIN_TOKEN_LENGTH) {
        frequencies.merge(term, 1, Int::plus)
      }
      true
    }
    if (frequencies.size <= MAX_TERMS_PER_DOCUMENT) return frequencies
    return frequencies.keys
      .sortedWith(compareByDescending<String> { it.length }.thenBy { it })
      .take(MAX_TERMS_PER_DOCUMENT)
      .associateWith { frequencies.getValue(it) }
  }
}
