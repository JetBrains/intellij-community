package com.intellij.grazie.utils

import ai.grazie.detector.ChainLanguageDetector
import ai.grazie.detector.DefaultLanguageDetectors
import ai.grazie.gec.model.problem.ProblemHighlighting
import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.Language.UNKNOWN
import ai.grazie.utils.mpp.FromResourcesDataLoader
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.detection.BatchLangDetector
import com.intellij.grazie.detection.LangDetector
import com.intellij.grazie.spellcheck.SpellingTextChecker
import com.intellij.grazie.text.CheckerRunner
import com.intellij.grazie.text.ProblemFilter
import com.intellij.grazie.text.TextChecker
import com.intellij.grazie.text.TextChecker.ProofreadingContext
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContent.TextDomain
import com.intellij.grazie.text.TextContentImpl
import com.intellij.grazie.text.TextProblem
import com.intellij.grazie.text.TreeRuleChecker
import com.intellij.grazie.utils.HighlightingUtil.findInstalledLang
import com.intellij.grazie.utils.HighlightingUtil.isTooLargeFile
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.util.runWithCheckCanceled
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.spellchecker.engine.DictionaryModificationTracker
import com.intellij.util.SlowOperations
import com.intellij.util.ui.EDT
import kotlinx.coroutines.runBlocking
import ai.grazie.text.TextRange as GrazieTextRange

@JvmField
internal val EXTRACTOR_SOURCE: Key<PsiElement> = Key("TextContent extractor source element")

@JvmOverloads
internal fun getAllProblems(file: PsiFile, checkedDomains: Set<TextDomain>, allCheckers: List<TextChecker> = TextChecker.allCheckers()): List<TextProblem> {
  val texts = HighlightingUtil.getAllFileTexts(file.viewProvider)
    .filter { ProblemFilter.allIgnoringFilters(it).findAny().isEmpty }
  texts.forEach { text -> require(text.getUserData(EXTRACTOR_SOURCE) != null) { "Text should contain source element" } }
  if (isTooLargeFile(texts)) return emptyList()

  return getAllProblems(texts, checkedDomains, allCheckers)
}

private fun getAllProblems(texts: List<TextContent>, checkedDomains: Set<TextDomain>, allCheckers: List<TextChecker>): List<TextProblem> =
  buildProblemMap(allCheckers, texts, checkedDomains)
    .flatMap { (_, problems) -> removeIntersecting(problems) }

private fun removeIntersecting(problems: List<TextProblem>): List<TextProblem> {
  val result = ArrayList<TextProblem>()
  for (problem in problems.sortedByDescending(::problemPriority)) {
    if (result.none { prev -> prev.highlightRanges.any { range -> problem.highlightRanges.any(range::intersects) } }) {
      result.add(problem)
    }
  }
  return result
}

private fun problemPriority(problem: TextProblem): Int = when {
  problem.isStyleLike -> 0
  problem !is TreeRuleChecker.TreeProblem -> 30
  problem.match.concedeToOtherGrammarCheckers() -> 10
  else -> 40
}

private fun buildProblemMap(
  allCheckers: List<TextChecker>,
  texts: List<TextContent>,
  checkedDomains: Set<TextDomain>,
): Map<TextContent, List<TextProblem>> {
  if (texts.isEmpty()) return emptyMap()
  val textsWithProblems = mutableMapOf<TextContent, MutableList<TextProblem>>()
  CheckerRunner.checkTexts(allCheckers, texts, checkedDomains).forEach { problem ->
    textsWithProblems.computeIfAbsent(problem.text) { ArrayList() }.add(problem)
  }
  return textsWithProblems
}

@JvmOverloads
fun getLanguageIfAvailable(text: TextContent, strippedOffset: Int? = null): Language? {
  val offset = strippedOffset ?: HighlightingUtil.stripPrefix(text)
  // Rider `ExternalTextContent` doesn't support view providers, hence batch detection is not available
  val language = if (text is TextContentImpl) {
    BatchLangDetector.getLanguage(text, offset)
  } else {
    LangDetector.getLanguage(text, offset)
  }
  return language?.takeIf { findInstalledLang(it) != null }
}

fun GrazieTextRange.Companion.coveringIde(ranges: Array<GrazieTextRange>): TextRange? {
  if (ranges.isEmpty()) return null
  return TextRange(ranges.minOf { it.start }, ranges.maxOf { it.endExclusive })
}

fun TextContent.toProofreadingContext(languageDetectionRequired: Boolean = true): ProofreadingContext {
  val content = this
  val prefix = HighlightingUtil.stripPrefix(content)
  val language = if (languageDetectionRequired) getLanguageIfAvailable(content, prefix) ?: UNKNOWN else UNKNOWN
  return object : ProofreadingContext {
    override fun getText(): TextContent = content
    override fun getLanguage(): Language = language
    override fun getStripPrefix(): String = content.toString().substring(0, prefix)
    override fun toString(): String =
      "[text='$content', language=$language, markupOffsets=${content.markupOffsets().toList()}, unknownOffsets=${content.unknownOffsets().toList()}, prefix='$prefix']"
  }
}

fun List<TextContent>.toProofreadingContext(languageDetectionRequired: Boolean = true): List<ProofreadingContext> =
  map { it.toProofreadingContext(languageDetectionRequired) }

internal fun ProofreadingContext.hasLanguage(): Boolean = this.language != UNKNOWN && findInstalledLang(this.language) != null
internal fun TextChecker.isSpelling(): Boolean = this is SpellingTextChecker
internal fun TextChecker.isGrammar(): Boolean = this !is SpellingTextChecker

val ProblemHighlighting.underline: TextRange?
  get() = GrazieTextRange.coveringIde(this.always)

internal fun getGrazieTracker(file: PsiFile): ModificationTracker {
  return ModificationTracker {
    service<GrazieConfig>().modificationCount +
    DictionaryModificationTracker.getInstance(file.project).modificationCount +
    file.modificationStamp +
    PsiModificationTracker.getInstance(file.project).modificationCount
  }
}

internal fun <T> runWithCheckCanceledOrKnownSlow(ytTicket: String, block: suspend () -> T): T {
  if (EDT.isCurrentThreadEdt()) {
    @Suppress("SSBasedInspection")
    return SlowOperations.knownIssue(ytTicket).use { runBlocking { block() } }
  } else {
    return runWithCheckCanceled { block() }
  }
}

object LanguageDetectorHolder {
  const val LIMIT: Int = 1_000
  
  @Volatile
  private var INSTANCE: ChainLanguageDetector<String>? = null
  private val lock = Any()

  fun get(): ChainLanguageDetector<String> {
    if (INSTANCE == null) {
      synchronized(lock) {
        if (INSTANCE == null) {
          INSTANCE = runBlockingCancellable {
            DefaultLanguageDetectors.standardForLanguages(Language.all.toLinkedSet(), FromResourcesDataLoader)
          }
        }
      }
    }
    return INSTANCE!!
  }
}
