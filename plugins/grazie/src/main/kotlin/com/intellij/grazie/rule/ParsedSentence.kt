package com.intellij.grazie.rule

import ai.grazie.nlp.langs.Language
import ai.grazie.rules.tree.StubbedSentence
import ai.grazie.rules.tree.Tree
import ai.grazie.text.exclusions.SentenceWithExclusions
import com.intellij.grazie.cloud.DependencyParser
import com.intellij.grazie.rule.ParsedSentence.Companion.getSentences
import com.intellij.grazie.rule.SentenceBatcher.AsyncBatchParser
import com.intellij.grazie.text.TextChecker.ProofreadingContext
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.utils.HighlightingUtil
import com.intellij.grazie.utils.HighlightingUtil.checkedDomains
import com.intellij.grazie.utils.NaturalTextDetector.seemsNatural
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import java.util.Objects
import java.util.SequencedMap

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

  @JvmField val tree: Tree?,

  /**
   * The range of the sentence in [extractedText] as reported by the sentence tokenizer,
   * including leading or trailing space
   */
  @JvmField val untrimmedRange: TextRange,
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
      return sentences.lastOrNull { it.tree != null && it.fileOffsetToText(fileOffset) != null }
    }

    /**
     * Finds a sentence as soon as possible, without invoking parser for any other sentences in the given file.
     * Use this method on explicit user actions only.
     */
    @JvmStatic
    fun findSentenceASAP(text: TextContent, fileOffset: Int): ParsedSentence? {
      val sentences = runBlockingCancellable { getSentences(text, TextRange.from(fileOffset, 0), minimal = true) }
      return sentences.lastOrNull { it.tree != null && it.fileOffsetToText(fileOffset) != null }
    }

    @JvmStatic
    fun getSentences(content: TextContent): List<ParsedSentence> {
      return runBlockingCancellable { getSentencesAsync(content) }
    }

    @JvmStatic
    fun getAllCheckedSentences(viewProvider: FileViewProvider): SequencedMap<TextContent, List<ParsedSentence>> {
      val contents = HighlightingUtil.getCheckedFileTexts(viewProvider).filterNot { HighlightingUtil.isTooLargeText(it) }
      if (contents.isEmpty()) return LinkedHashMap()

      return runBlockingCancellable {
        contents.associateWith { getSentencesAsync(it) } as SequencedMap<TextContent, List<ParsedSentence>>
      }
    }

    @JvmStatic
    suspend fun getAllCheckedSentences(texts: List<TextContent>): SequencedMap<TextContent, List<ParsedSentence>> {
      return getAllCheckedSentences(texts) { DependencyParser.getParser(it, false) }
    }

    internal suspend fun getAllCheckedSentences(texts: List<TextContent>, parser: (TextContent) -> AsyncBatchParser<Tree>?): SequencedMap<TextContent, List<ParsedSentence>> {
      val checkedDomains = checkedDomains()
      val contents = texts.filter { it.domain in checkedDomains && !HighlightingUtil.isTooLargeText(it) && seemsNatural(it) }
      if (contents.isEmpty()) return LinkedHashMap()

      return contents.associateWith { content ->
        parser(content)?.let { getSentences(content, content.commonParent.textRange, it) } ?: emptyList()
      } as SequencedMap<TextContent, List<ParsedSentence>>
    }

    internal fun getAllCheckedSentences(
      contexts: List<ProofreadingContext>, treesByLanguage: Map<Language, Map<SentenceWithExclusions, Tree?>>,
    ): SequencedMap<TextContent, List<ParsedSentence>> {
      val checkedDomains = checkedDomains()
      return contexts.asSequence()
        .filter { it.text.domain in checkedDomains }
        .filterNot { HighlightingUtil.isTooLargeText(it.text) }
        .filter { seemsNatural(it.text) }
        .mapNotNull {
          val trees = treesByLanguage[it.language]
          if (trees == null) return@mapNotNull null
          it.text to trees
        }.associate { (content, trees) ->
          content to getSentences(content, content.commonParent.textRange, trees)
        } as SequencedMap<TextContent, List<ParsedSentence>>
    }

    suspend fun getSentencesAsync(content: TextContent): List<ParsedSentence> {
      return getSentences(content, content.commonParent.textRange, minimal = false)
    }

    @Suppress("unused")
    suspend fun getSentencesAsync(context: ProofreadingContext): List<ParsedSentence> {
      if (HighlightingUtil.isTooLargeText(listOf(context.text))) return emptyList()
      val parser = DependencyParser.getParser(context, false) ?: return emptyList()
      return getSentences(context.text, context.text.commonParent.textRange, parser)
    }

    private suspend fun getSentences(content: TextContent, rangeInFile: TextRange, minimal: Boolean): List<ParsedSentence> {
      if (HighlightingUtil.isTooLargeText(listOf(content)) || !seemsNatural(content)) {
        return emptyList()
      }
      val parser = DependencyParser.getParser(content, minimal) ?: return emptyList()
      return getSentences(content, rangeInFile, parser)
    }

    private suspend fun getSentences(content: TextContent, rangeInFile: TextRange, parser: AsyncBatchParser<Tree>): List<ParsedSentence> {
      val intersectingSentences =
        SentenceTokenizer.tokenize(content).filter { token ->
          val start = content.textOffsetToFile(token.start)
          val end = content.textOffsetToFile(token.end())
          rangeInFile.intersects(start, end)
        }
      if (intersectingSentences.isNotEmpty()) {
        val trees = parser.parseAsync(intersectingSentences.flatMap { listOfNotNull(it.swe(), it.stubbedSwe()) })
        return getSentences(content, intersectingSentences, trees)
      }
      return emptyList()
    }

    private fun getSentences(content: TextContent, rangeInFile: TextRange, trees: Map<SentenceWithExclusions, Tree?>): List<ParsedSentence> {
      val intersectingSentences = SentenceTokenizer.tokenize(content).filter { token ->
        val start = content.textOffsetToFile(token.start)
        val end = content.textOffsetToFile(token.end())
        rangeInFile.intersects(start, end)
      }
      return getSentences(content, intersectingSentences, trees)
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
