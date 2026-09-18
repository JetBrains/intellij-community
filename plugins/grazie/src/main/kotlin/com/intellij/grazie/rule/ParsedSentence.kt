package com.intellij.grazie.rule

import ai.grazie.nlp.langs.Language
import ai.grazie.rules.tree.StubbedSentence
import ai.grazie.rules.tree.Tree
import ai.grazie.text.exclusions.SentenceWithExclusions
import com.intellij.grazie.cloud.DependencyParser
import com.intellij.grazie.rule.ParsedSentence.Companion.getSentences
import com.intellij.grazie.text.TextChecker.ProofreadingContext
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.utils.HighlightingUtil
import com.intellij.grazie.utils.HighlightingUtil.checkedDomains
import com.intellij.grazie.utils.NaturalTextDetector.seemsNatural
import com.intellij.grazie.utils.hasLanguage
import com.intellij.openapi.util.TextRange
import java.util.Objects
import java.util.SequencedMap

/**
 * An object representing a parsed sentence contained in some PSI elements, providing ways to access the sentence's
 * syntactic structure and to convert offsets from PSI representation into natural language text and back.
 * The instances are usually obtained via [getSentences] or [getAllCheckedSentences]
 */
class ParsedSentence private constructor(
  /** The start of this sentence in [extractedText] */
  @JvmField val textStartOffset: Int,

  /** The text of the sentence in natural language, without PSI markup and leading or trailing space */
  @JvmField val text: String,

  /** The underlying text  */
  @JvmField val extractedText: TextContent,

  @JvmField val tree: Tree?,

  /**
   * The range of the sentence in [extractedText] as reported by the sentence tokenizer,
   * including leading or trailing space
   */
  @JvmField val untrimmedRange: TextRange,
) {
  override fun toString(): String = text

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    return other is ParsedSentence &&
           textStartOffset == other.textStartOffset && text == other.text && extractedText == other.extractedText
  }

  override fun hashCode(): Int = Objects.hash(text, textStartOffset, extractedText)

  companion object {
    @JvmStatic
    suspend fun getAllCheckedSentences(contexts: List<ProofreadingContext>): SequencedMap<TextContent, List<ParsedSentence>> {
      val checkedDomains = checkedDomains()
      val contents = contexts.filter { it.text.domain in checkedDomains && it.hasLanguage() }
      if (contents.isEmpty()) return LinkedHashMap()

      return contents.associateTo(LinkedHashMap()) { it.text to getSentences(it) }
    }

    @JvmStatic
    suspend fun getSentences(context: ProofreadingContext): List<ParsedSentence> {
      val content = context.text
      if (HighlightingUtil.isTooLargeText(content) || !seemsNatural(content)) {
        return emptyList()
      }
      return getSentences(content, content.commonParent.textRange, context.language)
    }

    private suspend fun getSentences(content: TextContent, rangeInFile: TextRange, language: Language): List<ParsedSentence> {
      val intersectingSentences =
        SentenceTokenizer.tokenize(content).filter { token ->
          val start = content.textOffsetToFile(token.start)
          val end = content.textOffsetToFile(token.end())
          rangeInFile.intersects(start, end)
        }
      if (intersectingSentences.isNotEmpty()) {
        val trees = DependencyParser.parse(
          language, intersectingSentences.flatMap { listOfNotNull(it.swe(), it.stubbedSwe()) }
        )
        return getSentences(content, intersectingSentences, trees)
      }
      return emptyList()
    }

    private fun getSentences(content: TextContent, intersectingSentences: List<SentenceTokenizer.Sentence>, trees: Map<SentenceWithExclusions, Tree?>): List<ParsedSentence> {
      val out = ArrayList<ParsedSentence>()
      for (sentence in intersectingSentences) {
        val untrimmedRange = TextRange(sentence.start, sentence.end())
        var tree = trees[sentence.swe()]
        if (tree != null) {
          val start = sentence.start
          tree = tree.withStartOffset(start)
          val stubbed = trees[sentence.stubbedSwe()]
          if (stubbed != null) tree = tree.withStubbed(StubbedSentence(sentence.swe(), stubbed.withStartOffset(start)))
          out.add(ParsedSentence(tree.startOffset(), tree.text(), content, tree, untrimmedRange))
        }
        else {
          out.add(ParsedSentence(sentence.start, sentence.text, content, null, untrimmedRange))
        }
      }
      return out
    }
  }
}
