package com.intellij.grazie.spellcheck

import ai.grazie.gec.model.CorrectionServiceType
import ai.grazie.gec.model.problem.Problem
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
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.cloud.APIQueries
import com.intellij.grazie.cloud.GrazieCloudConnector
import com.intellij.grazie.mlec.LanguageHolder
import com.intellij.grazie.rule.SentenceBatcher
import com.intellij.grazie.spellcheck.engine.GrazieSpellCheckerEngine
import com.intellij.grazie.text.ExternalTextChecker
import com.intellij.grazie.text.Rule
import com.intellij.grazie.text.TextChecker.ProofreadingContext
import com.intellij.grazie.text.TextProblem
import com.intellij.grazie.utils.EXTRACTOR_SOURCE
import com.intellij.grazie.utils.NaturalTextDetector
import com.intellij.grazie.utils.getProblems
import com.intellij.grazie.utils.getTextProblems
import com.intellij.grazie.utils.toLinkedSet
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil.BombedCharSequence
import com.intellij.psi.PsiFile
import com.intellij.spellchecker.SpellCheckerManager
import com.intellij.spellchecker.engine.DictionaryModificationTracker
import com.intellij.spellchecker.inspections.IdentifierSplitter.MINIMAL_TYPO_LENGTH
import com.intellij.spellchecker.inspections.SpellCheckingInspection.SpellCheckingScope
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy.getSpellcheckingStrategy
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val spellingKey = Key.create<CachedResults>("grazie.text.spell.problems")

internal class SpellingTextChecker : ExternalTextChecker() {
  override fun getRules(locale: Locale): Collection<Rule> = emptyList()

  override fun check(context: ProofreadingContext): Collection<TypoProblem> =
    doCheck(context) {
      findLocalTypos(context, it)
    }

  override suspend fun checkExternally(context: ProofreadingContext): Collection<TypoProblem> =
    doCheck(context) {
      findTypos(context, it)
    }

  private inline fun doCheck(context: ProofreadingContext, action: (Project) -> List<TypoProblem>): List<TypoProblem> {
    val file = context.text.containingFile
    val project = file.project
    val scopes = GrazieSpellCheckingInspection.buildAllowedScopes(file)
    if (!useTextLevelSpellchecking(context, scopes)) return emptyList()

    val configStamp = getConfigStamp(project)
    var cache = getCachedTypos(context, configStamp)
    if (cache == null) {
      cache = action(project)
        .filterNot { it.word.length < MINIMAL_TYPO_LENGTH }
        .filterNot { hasUnknownFragments(it) }
      context.text.putUserData(spellingKey, CachedResults(configStamp, cache))
    }
    return cache
  }

  override fun check(contexts: List<ProofreadingContext>): Collection<TextProblem> {
    if (!Registry.`is`("grazie.correct.text.enabled")) return super.check(contexts)
    return doCheck(contexts) { contexts, project ->
      findLocalTypos(contexts, project)
    }
  }

  override suspend fun checkExternally(contexts: List<ProofreadingContext>): Collection<TextProblem> {
    if (!Registry.`is`("grazie.correct.text.enabled")) return super.checkExternally(contexts)
    return doCheck(contexts) { contexts, project ->
      findTypos(contexts, project)
    }
  }

  private inline fun doCheck(contexts: List<ProofreadingContext>, action: (List<ProofreadingContext>, Project) -> List<TypoProblem>): Collection<TextProblem> {
    if (contexts.isEmpty()) return emptyList()

    val file = contexts.first().text.containingFile
    val project = file.project
    val scopes = GrazieSpellCheckingInspection.buildAllowedScopes(file)
    val toCheck = contexts.filter { useTextLevelSpellchecking(it, scopes) }
    if (toCheck.isEmpty()) return emptyList()

    val configStamp = getConfigStamp(project)
    var cache = getCachedTypos(file, configStamp)
    if (cache == null) {
      cache = action(toCheck, project)
        .filterNot { it.word.length < MINIMAL_TYPO_LENGTH }
        .filterNot { hasUnknownFragments(it) }
      file.putUserData(spellingKey, CachedResults(configStamp, cache))
    }
    return cache
  }

  private fun useTextLevelSpellchecking(context: ProofreadingContext, scopes: Set<SpellCheckingScope>): Boolean {
    val element = context.text.getUserData(EXTRACTOR_SOURCE) ?: return true
    val strategy = getSpellcheckingStrategy(element)
    return strategy != null && strategy.elementFitsScope(element, scopes) && strategy.useTextLevelSpellchecking(element)
  }

  private fun getConfigStamp(project: Project): Long =
    service<GrazieConfig>().modificationCount +
    DictionaryModificationTracker.getInstance(project).modificationCount

  private fun getCachedTypos(context: ProofreadingContext, configStamp: Long): List<TypoProblem>? {
    val cache = context.text.getUserData(spellingKey)
    if (cache != null && cache.configStamp == configStamp) {
      return cache.problems
    }
    return null
  }

  private fun getCachedTypos(file: PsiFile, configStamp: Long): List<TypoProblem>? {
    val cache = file.getUserData(spellingKey)
    if (cache != null && cache.configStamp == configStamp) {
      return cache.problems
    }
    return null
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
          .map { lang -> GrazieSpellCheckerEngine.knownPhrases.computeIfAbsent(lang) { KnownPhrases.forLanguage(lang) } }
          .flatMap {
            ProgressManager.checkCanceled()
            it.validPhrases(text)
          }
          .map { it.range() }
          .toList()
      }
    }
  }

  private fun findLocalTypos(contexts: List<ProofreadingContext>, project: Project): List<TypoProblem> {
    return checkText(contexts, project).flatMap { entry ->
      entry.value.map { toProblem(entry.key, it) }
    }
  }

  private suspend fun findTypos(contexts: List<ProofreadingContext>, project: Project): List<TypoProblem> {
    val contextsWithProblems = checkText(contexts, project)
    return findTyposInCloud(contexts, contextsWithProblems, project)
  }

  private fun findLocalTypos(context: ProofreadingContext, project: Project): List<TypoProblem> =
    checkText(context, project).map { toProblem(context, it) }

  private suspend fun findTypos(context: ProofreadingContext, project: Project): List<TypoProblem> {
    val localTypos = checkText(context, project)
    return findTyposInCloud(context, localTypos, project)
  }

  private suspend fun findTyposInCloud(contexts: List<ProofreadingContext>, localTypos: Map<ProofreadingContext, List<Typo>>, project: Project): List<TypoProblem> {
    if (!Registry.`is`("spellchecker.cloud.enabled", false)
        || localTypos.isEmpty()
        || !GrazieCloudConnector.seemsCloudConnected()
        || GrazieCloudConnector.isAfterRecentGecError()
    ) {
      return localTypos.flatMap { entry -> entry.value.map { toProblem(entry.key, it) } }
    }

    val cloudTypos = getTextProblems(contexts, CorrectionServiceType.SPELL)?.takeIf { it.isNotEmpty() }
    if (cloudTypos == null) return localTypos.flatMap { entry -> entry.value.map { toProblem(entry.key, it) } }

    val manager = SpellCheckerManager.getInstance(project)
    return localTypos.flatMap { (context, problems) ->
      val cloudProblems = cloudTypos[context] ?: return@flatMap problems.map { toProblem(context, it) }
      toCloudProblems(context, cloudProblems, manager)
    }
  }

  private suspend fun findTyposInCloud(context: ProofreadingContext, localTypos: List<Typo>, project: Project): List<TypoProblem> {
    if (!Registry.`is`("spellchecker.cloud.enabled", false)
        || localTypos.isEmpty()
        || !GrazieCloudConnector.seemsCloudConnected()
        || GrazieCloudConnector.isAfterRecentGecError()
        || !NaturalTextDetector.seemsNatural(context.text)
        || context.language == Language.UNKNOWN
    ) {
      return localTypos.map { toProblem(context, it) }
    }

    val cloudTypos = getProblems(context, SpellServerBatcherHolder::class.java)
    if (cloudTypos == null) return localTypos.map { toProblem(context, it) }

    val manager = SpellCheckerManager.getInstance(project)
    return toCloudProblems(context, cloudTypos, manager)
  }

  private fun toCloudProblems(context: ProofreadingContext, problems: List<Problem>, manager: SpellCheckerManager): List<TypoProblem> =
    problems
      .mapNotNull {
        val parts = it.fixes.flatMap { fix -> fix.parts.toList() }
          .filterIsInstance<ProblemFix.Part.Change>()
          .filter { part -> part.type == ProblemFix.Part.Change.ChangeType.REPLACE }
        if (parts.isEmpty()) return@mapNotNull null

        val word = parts.first().range.substring(context.text.toString())
        if (!manager.hasProblem(word)) return@mapNotNull null

        TypoProblem(context.text, parts.first().range, word, true) {
          parts.map { part -> part.text }.toLinkedSet()
        }
      }

  private fun checkText(context: ProofreadingContext, project: Project): List<Typo> {
    val textSpeller = getTextSpeller(project) ?: return emptyList()
    return textSpeller.checkText(object : BombedCharSequence(context.text) {
      override fun checkCanceled() {
        ProgressManager.checkCanceled()
      }
    })
  }

  private fun checkText(contexts: List<ProofreadingContext>, project: Project): Map<ProofreadingContext, List<Typo>> {
    val textSpeller = getTextSpeller(project) ?: return emptyMap()
    return contexts.associateWith {
      textSpeller.checkText(object : BombedCharSequence(it.text) {
        override fun checkCanceled() {
          ProgressManager.checkCanceled()
        }
      })
    }
  }

  private fun hasUnknownFragments(typo: TypoProblem): Boolean {
    return typo.text.unknownOffsets().any { offset ->
      offset > typo.range.startOffset && offset < typo.range.endOffset
    }
  }

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

private fun toProblem(context: ProofreadingContext, typo: Typo) = TypoProblem(context.text, typo.range, typo.word, false) { typo.fixes }

private data class CachedResults(val configStamp: Long, val problems: List<TypoProblem>)