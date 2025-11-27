package com.intellij.grazie.spellcheck

import ai.grazie.gec.model.problem.ProblemFix
import ai.grazie.gec.model.problem.SentenceWithProblems
import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.LanguageWithVariant
import ai.grazie.nlp.tokenizer.Tokenizer
import ai.grazie.nlp.utils.checkedEndExclusive
import ai.grazie.rules.common.KnownPhrases
import ai.grazie.spell.Speller
import ai.grazie.spell.text.TextSpeller
import ai.grazie.spell.text.Typo
import ai.grazie.text.exclusions.SentenceWithExclusions
import ai.grazie.utils.LinkedSet
import ai.grazie.utils.toLinkedSet
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieConfig.State.Processing
import com.intellij.grazie.cloud.APIQueries
import com.intellij.grazie.cloud.GrazieCloudConnector
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection.Companion.sortByPriority
import com.intellij.grazie.mlec.LanguageHolder
import com.intellij.grazie.rule.SentenceBatcher
import com.intellij.grazie.spellcheck.engine.GrazieSpellCheckerEngine
import com.intellij.grazie.text.ProblemFilter
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.utils.NaturalTextDetector
import com.intellij.grazie.utils.getProblems
import com.intellij.grazie.utils.toProofreadingContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil.BombedCharSequence
import com.intellij.psi.PsiElement
import com.intellij.spellchecker.SpellCheckerManager
import com.intellij.spellchecker.inspections.IdentifierSplitter.MINIMAL_TYPO_LENGTH
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Consumer

private val DOMAINS = TextContent.TextDomain.ALL

object GrazieTextLevelSpellCheckingExtension {

  private val knownPhrases = ContainerUtil.createConcurrentSoftValueMap<Language, KnownPhrases>()


  /**
   * Performs spell-checking on the specified PSI element.
   *
   * The implementation may examine neighboring elements of [PsiElement] if needed.
   * In case of doing that, it's implementation's responsibility to not check the same element for spelling mistake twice.
   *
   * @param element The PSI element to check for spelling errors
   * @param strategy The element's spellchecking strategy
   * @param consumer The callback function that will be invoked for each spelling error detected during the inspection
   *
   * @return [SpellCheckingResult.Checked] if the PSI element has been checked, [SpellCheckingResult.Ignored] otherwise
   */
  fun spellcheck(element: PsiElement, strategy: SpellcheckingStrategy, session: LocalInspectionToolSession, consumer: Consumer<SpellingTypo>): SpellCheckingResult {
    if (!strategy.useTextLevelSpellchecking()) return SpellCheckingResult.Ignored
    ProgressManager.checkCanceled()

    val texts = sortByPriority(TextExtractor.findTextsExactlyAt(element, DOMAINS), session.priorityRange)
    if (texts.isEmpty()) return SpellCheckingResult.Ignored
    if (GrazieInspection.skipCheckingTooLargeTexts(texts)) return SpellCheckingResult.Checked

    val filteredTexts = texts.filter { ProblemFilter.allIgnoringFilters(it).findAny().isEmpty }
    if (filteredTexts.isEmpty()) return SpellCheckingResult.Checked

    val textSpeller = getTextSpeller(element.project) ?: return SpellCheckingResult.Ignored
    filteredTexts.asSequence()
      .map { it to findTypos(it, session, textSpeller, element.project) }
      .flatMap { mapTypo(it.first, it.second, element) }
      .filterNot { it.word.length < MINIMAL_TYPO_LENGTH }
      .forEach { consumer.accept(it) }
    return SpellCheckingResult.Checked
  }

  private fun getTextSpeller(project: Project): TextSpeller? {
    val speller = GrazieSpellCheckerEngine.getInstance(project).getSpeller() ?: return null
    val enabledLanguages = GrazieConfig.get().enabledLanguages.mapNotNull { it.withVariant }
    val validPhrasesTexts = ConcurrentHashMap<CharSequence, List<ai.grazie.rules.tree.TextRange>>()

    return object : TextSpeller(listOf(object : Speller by speller {
      override fun languages(): List<LanguageWithVariant> = enabledLanguages
    })) {
      override fun ignoreInContext(word: Tokenizer.Token, text: CharSequence): Boolean {
        return super.ignoreInContext(word, text) || isRangeCoveredByValidPhrase(word, text)
      }

      private fun isRangeCoveredByValidPhrase(word: Tokenizer.Token, text: CharSequence): Boolean {
        val textRanges = validPhrasesTexts.getOrPut(text) { getValidPhraseRanges(text) }
        return textRanges.any { it.start <= word.range.first && it.end <= word.range.checkedEndExclusive }
      }

      private fun getValidPhraseRanges(text: CharSequence): List<ai.grazie.rules.tree.TextRange> {
        return enabledLanguages
          .asSequence()
          .map { it.base }
          .filter { it in KnownPhrases.SUPPORTED_LANGUAGES }
          .map { lang -> knownPhrases.computeIfAbsent(lang) { KnownPhrases.forLanguage(lang) } }
          .flatMap {
            ProgressManager.checkCanceled()
            it.validPhrases(text)
          }
          .toList()
      }
    }
  }

  private fun mapTypo(text: TextContent, typos: List<Typo>, element: PsiElement): List<SpellingTypo> {
    val psiRange = element.textRange
    return typos.mapNotNull { typo ->
      val range = text.textRangeToFile(mapRange(typo.range))
      if (!psiRange.contains(range)) return@mapNotNull null
      val shiftedRange = range.shiftLeft(element.textRange.startOffset)

      val hasUnknownFragmentsInside = text.unknownOffsets().any { offset ->
        offset > typo.range.start && offset < typo.range.endExclusive
      }
      if (hasUnknownFragmentsInside) return@mapNotNull null

      createTypo(typo.word, shiftedRange, element) {
        if (typo is CloudTypo) typo.fixes else LinkedSet()
      }
    }
  }

  private fun mapRange(range: ai.grazie.text.TextRange): TextRange = TextRange(range.start, range.endExclusive)

  private fun findTypos(text: TextContent, session: LocalInspectionToolSession, textSpeller: TextSpeller, project: Project): List<Typo> {
    val typos = session.getOrCreateUserData(KEY_TYPO_CACHE) { ConcurrentHashMap() }
    val detectedTypos = typos[text]
    if (detectedTypos != null) return detectedTypos
    val localTypos = textSpeller.checkText(object : BombedCharSequence(text) {
      override fun checkCanceled() {
        ProgressManager.checkCanceled()
      }
    })
    return typos.computeIfAbsent(text) { findTyposInCloud(text, localTypos, project) }
  }

  private fun findTyposInCloud(text: TextContent, localTypos: List<Typo>, project: Project): List<Typo> {
    if (!Registry.`is`("spellchecker.cloud.enabled", false)
        || localTypos.isEmpty()
        || GrazieConfig.get().processing == Processing.Local
        || !GrazieCloudConnector.seemsCloudConnected()
        || GrazieCloudConnector.isAfterRecentGecError()
        || !NaturalTextDetector.seemsNatural(text)) {
      return localTypos
    }

    val context = text.toProofreadingContext()
    val cloudTypos = runBlockingCancellable { getProblems(context, SpellServerBatcherHolder::class.java) }
    if (cloudTypos == null) return localTypos

    val manager = SpellCheckerManager.getInstance(project)
    return cloudTypos
      .mapNotNull {
        val parts = it.fixes.flatMap { fix -> fix.parts.toList() }
          .filterIsInstance<ProblemFix.Part.Change>()
          .filter { part -> part.type == ProblemFix.Part.Change.ChangeType.REPLACE }
        if (parts.isEmpty()) return@mapNotNull null

        val word = parts.first().range.substring(text.toString())
        if (!manager.hasProblem(word)) return@mapNotNull null

        CloudTypo(word, parts.first().range, parts.map { part -> part.text }.toLinkedSet())
      }
  }

  private fun createTypo(word: String, range: TextRange, element: PsiElement, lazyFixes: () -> LinkedSet<String>) = object : SpellingTypo {
    override val word: String = word
    override val range: TextRange = range
    override val element: PsiElement = element
    override val fixes: LinkedSet<String> = lazyFixes()
  }

  private class CloudTypo(override val word: String, override val range: ai.grazie.text.TextRange, override val fixes: LinkedSet<String>) : Typo

  @Service
  private class SpellServerBatcherHolder : LanguageHolder<SentenceBatcher<SentenceWithProblems>>() {
    private class ServerBatcher(
      language: Language,
    ) : SentenceBatcher<SentenceWithProblems>(language, 32), Disposable {
      override suspend fun parse(sentences: List<SentenceWithExclusions>, project: Project): Map<SentenceWithExclusions, SentenceWithProblems>? {
        return APIQueries.spell(sentences, language, project)
          ?.zip(sentences)
          ?.associate { it.second to it.first }
      }

      override fun dispose() {}

      init {
        GrazieConfig.subscribe(this) { clearCache() }
        GrazieCloudConnector.subscribeToAuthorizationStateEvents(this) { clearCache() }
      }
    }

    init {
      update(mapOf(
        Language.ENGLISH to ServerBatcher(Language.ENGLISH),
        Language.UKRAINIAN to ServerBatcher(Language.UKRAINIAN),
        Language.GERMAN to ServerBatcher(Language.GERMAN),
        Language.RUSSIAN to ServerBatcher(Language.RUSSIAN)
      ))
    }
  }
}

/** A typo detected by [GrazieTextLevelSpellCheckingExtension] in a sentence or text inside a [PsiElement]. */
interface SpellingTypo {
  /** The misspelled word inside the [element] */
  val word: String

  /** The range of the typo in the [element]'s text */
  val range: TextRange

  /** Element that contains a misspelled [word] within the given text [range] */
  val element: PsiElement

  /** Suggested corrections for the [word], possibly calculated lazily */
  val fixes: LinkedSet<String>
}

enum class SpellCheckingResult { Checked, Ignored }

private val KEY_TYPO_CACHE = Key.create<ConcurrentMap<TextContent, List<Typo>>>("KEY_TYPO_CACHE")