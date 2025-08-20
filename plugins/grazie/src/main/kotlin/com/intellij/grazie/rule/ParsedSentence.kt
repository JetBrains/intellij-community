package com.intellij.grazie.rule

import ai.grazie.rules.tree.StubbedSentence
import ai.grazie.rules.tree.Tree
import com.intellij.grazie.cloud.DependencyParser
import com.intellij.grazie.rule.ParsedSentence.Companion.findSentenceASAP
import com.intellij.grazie.rule.ParsedSentence.Companion.findSentenceInFile
import com.intellij.grazie.rule.ParsedSentence.Companion.getSentences
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.utils.HighlightingUtil
import com.intellij.grazie.utils.NaturalTextDetector
import com.intellij.grazie.utils.blockingContext
import com.intellij.grazie.utils.runBlockingCancellable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import java.util.*

/**
 * An object representing a parsed sentence contained in some PSI elements, providing ways to access the sentence's
 * syntactic structure and to convert offsets from PSI representation into natural language text and back.
 * The instances are usually obtained via [findSentenceInFile] or [getSentences].
 * To speed up highlighting, batching is used:
 * even when a single sentence is needed, the server request may contain other sentences from the same file.
 * To explicitly disable that, use [findSentenceASAP].
 */
class ParsedSentence private constructor(
  /** The start of this sentence in [extractedText] */
  @JvmField val textStartOffset: Int,

  /** The text of the sentence in natural language, without PSI markup and leading or trailing space */
  @JvmField val text: String,

  /** The underlying text  */
  @JvmField val extractedText: TextContent,

  /** The dependency tree for the sentence  */
  @JvmField val tree: Tree
) {

  fun textOffsetToFile(textOffset: Int): Int {
    return extractedText.textOffsetToFile(textOffset + textStartOffset)
  }

  fun fileOffsetToText(fileOffset: Int): Int? {
    val contentOffset = extractedText.fileOffsetToText(fileOffset) ?: return null
    val textOffset = contentOffset - textStartOffset
    return if (textOffset >= 0 && textOffset <= text.length) textOffset else null
  }

  override fun toString(): String = text

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    return other is ParsedSentence &&
           textStartOffset == other.textStartOffset && text == other.text && extractedText == other.extractedText
  }

  override fun hashCode(): Int = Objects.hash(text, textStartOffset, extractedText)

  companion object {
    @JvmStatic
    fun findSentenceInFile(file: PsiFile?, fileOffset: Int): ParsedSentence? {
      if (file == null) return null
      val text = TextExtractor.findTextAt(file, fileOffset, TextContent.TextDomain.ALL) ?: return null
      val sentences = runBlockingCancellable { getSentences(text, TextRange.from(fileOffset, 0), minimal = false) }
      return sentences.lastOrNull { it.fileOffsetToText(fileOffset) != null }
    }

    /**
     * Finds a sentence as soon as possible, without invoking parser for any other sentences in the given file.
     * Use this method on explicit user actions only.
     */
    @JvmStatic
    fun findSentenceASAP(text: TextContent, fileOffset: Int): ParsedSentence? {
      val sentences = runBlockingCancellable { getSentences(text, TextRange.from(fileOffset, 0), minimal = true) }
      return sentences.lastOrNull { it.fileOffsetToText(fileOffset) != null }
    }

    @JvmStatic
    fun getSentences(content: TextContent): List<ParsedSentence> {
      return runBlockingCancellable { getSentencesAsync(content) }
    }

    suspend fun getSentencesAsync(content: TextContent): List<ParsedSentence> {
      return getSentences(content, content.commonParent.textRange, minimal = false)
    }

    private suspend fun getSentences(content: TextContent, rangeInFile: TextRange,
                                     minimal: Boolean): List<ParsedSentence> {
      if (HighlightingUtil.isTooLargeText(listOf(content)) ||
          !NaturalTextDetector.seemsNatural(content.toString())) {
        return emptyList()
      }

      val parser = blockingContext { DependencyParser.getParser(content, minimal) } ?: return emptyList()

      val out = ArrayList<ParsedSentence>()
      val intersectingSentences = blockingContext {
        SentenceTokenizer.tokenize(content).filter { token ->
          val start = content.textOffsetToFile(token.start)
          val end = content.textOffsetToFile(token.end())
          rangeInFile.intersects(start, end)
        }
      }
      if (intersectingSentences.isNotEmpty()) {
        val trees = parser.parseAsync(intersectingSentences.flatMap { listOfNotNull(it.swe(), it.stubbedSwe()) })
        for (sentence in intersectingSentences) {
          var tree = trees[sentence.swe()]
          if (tree != null) {
            val start = sentence.start
            tree = tree.withStartOffset(start)!!
            val stubbed = trees[sentence.stubbedSwe()]
            if (stubbed != null) tree = tree.withStubbed(StubbedSentence(sentence.swe(), stubbed.withStartOffset(start)))!!
            out.add(ParsedSentence(tree.startOffset(), tree.text(), content, tree))
          }
        }
      }
      return out
    }
  }
}