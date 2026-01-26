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
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.cloud.APIQueries
import com.intellij.grazie.cloud.GrazieCloudConnector
import com.intellij.grazie.mlec.LanguageHolder
import com.intellij.grazie.rule.SentenceBatcher
import com.intellij.grazie.spellcheck.engine.GrazieSpellCheckerEngine
import com.intellij.grazie.text.ExternalTextChecker
import com.intellij.grazie.text.Rule
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.utils.NaturalTextDetector
import com.intellij.grazie.utils.getProblems
import com.intellij.grazie.utils.toLinkedSet
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil.BombedCharSequence
import com.intellij.spellchecker.SpellCheckerManager
import com.intellij.spellchecker.inspections.IdentifierSplitter.MINIMAL_TYPO_LENGTH
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val spellingKey = Key.create<CachedResults>("grazie.text.spell.problems")

internal class SpellingTextChecker: ExternalTextChecker() {
  override fun getRules(locale: Locale): Collection<Rule> = emptyList()
  override suspend fun checkExternally(context: ProofreadingContext): Collection<TypoProblem> {
    val configStamp = service<GrazieConfig>().modificationCount + SpellCheckerManager.dictionaryModificationTracker.modificationCount
    var cache = getCachedTypos(context.text, configStamp)
    if (cache == null) {
      cache = findTypos(context)
        .filterNot { it.word.length < MINIMAL_TYPO_LENGTH }
        .filterNot { hasUnknownFragments(it) }
      context.text.putUserData(spellingKey, CachedResults(configStamp, cache))
    }
    return cache
  }

  private fun getCachedTypos(text: TextContent, configStamp: Long): List<TypoProblem>? {
    val cache = text.getUserData(spellingKey)
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

  private suspend fun findTypos(context: ProofreadingContext): List<TypoProblem> {
    val project = context.text.containingFile.project
    val textSpeller = getTextSpeller(project) ?: return emptyList()
    val localTypos = textSpeller.checkText(object : BombedCharSequence(context.text) {
      override fun checkCanceled() {
        ProgressManager.checkCanceled()
      }
    })
    return findTyposInCloud(context, localTypos, project)
  }

  private suspend fun findTyposInCloud(context: ProofreadingContext, localTypos: List<Typo>, project: Project): List<TypoProblem> {
    if (!Registry.`is`("spellchecker.cloud.enabled", false)
        || localTypos.isEmpty()
        || !GrazieCloudConnector.seemsCloudConnected()
        || GrazieCloudConnector.isAfterRecentGecError()
        || !NaturalTextDetector.seemsNatural(context.text)
        || context.language == Language.UNKNOWN
      ) {
      return localTypos.map { toProblem(context.text, it) }
    }

    val cloudTypos = getProblems(context, SpellServerBatcherHolder::class.java)
    if (cloudTypos == null) return localTypos.map { toProblem(context.text, it) }

    val manager = SpellCheckerManager.getInstance(project)
    return cloudTypos
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

private fun toProblem(text: TextContent, typo: Typo) = TypoProblem(text, typo.range, typo.word, false) { typo.fixes }

private data class CachedResults(val configStamp: Long, val problems: List<TypoProblem>)