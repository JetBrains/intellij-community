package com.intellij.grazie.utils

import ai.grazie.detector.ChainLanguageDetector
import ai.grazie.detector.DefaultLanguageDetectors
import ai.grazie.gec.model.problem.ProblemHighlighting
import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.Language.UNKNOWN
import ai.grazie.rules.Rule
import ai.grazie.rules.settings.RuleSetting
import ai.grazie.rules.settings.Setting
import ai.grazie.rules.toolkit.LanguageToolkit
import ai.grazie.utils.mpp.FromResourcesDataLoader
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.detection.BatchLangDetector
import com.intellij.grazie.detection.LangDetector
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection.Companion.MAX_TEXT_LENGTH_IN_FILE
import com.intellij.grazie.ide.ui.configurable.StyleConfigurable.Companion.ruleEngineLanguages
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.rule.RuleIdeClient
import com.intellij.grazie.rule.SentenceTokenizer.toTokens
import com.intellij.grazie.spellcheck.SpellingTextChecker
import com.intellij.grazie.text.CheckerRunner
import com.intellij.grazie.text.ProblemFilter
import com.intellij.grazie.text.TextChecker
import com.intellij.grazie.text.TextChecker.ProofreadingContext
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContent.TextDomain
import com.intellij.grazie.text.TextContentImpl
import com.intellij.grazie.text.TextProblem
import com.intellij.grazie.text.TextProblemAggregator
import com.intellij.grazie.utils.HighlightingUtil.findInstalledLang
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.spellchecker.engine.DictionaryModificationTracker
import com.intellij.util.containers.CollectionFactory.createConcurrentSoftValueMap
import java.util.concurrent.ConcurrentHashMap
import ai.grazie.text.TextRange as GrazieTextRange

@JvmField
internal val EXTRACTOR_SOURCE: Key<PsiElement> = Key("TextContent extractor source element")

private val affectedGlobalRules = createConcurrentSoftValueMap<Language, Set<String>>()
private val associatedGrazieRules = ConcurrentHashMap<Language, Map<String, Rule>>()

fun getAssociatedGrazieRule(rule: com.intellij.grazie.text.Rule): Rule? {
  if (rule.language !in ruleEngineLanguages) return null
  return associatedGrazieRules
    .computeIfAbsent(rule.language) { buildAssociatedGrazieMapping(rule.language) }
    .get(rule.globalId)
}

fun getAffectedGlobalRules(language: Language): Set<String> {
  if (language !in ruleEngineLanguages) return emptySet()
  return affectedGlobalRules.computeIfAbsent(language) {
    val prefix = Rule.globalIdPrefix(language)
    featuredSettings(language)
      .filterIsInstance<RuleSetting>()
      .map { prefix + it.rule.id }
      .toHashSet()
  }
}

fun featuredSettings(language: Language): List<Setting> =
  LanguageToolkit.forLanguage(language).getSettings(RuleIdeClient.INSTANCE)
    .flatMap { it.settings() }
    .flatMap { setting ->
      when (setting) {
        is RuleSetting -> listOf(setting) + setting.subSettings
        else -> listOf(setting)
      }
    }

@JvmOverloads
internal fun getAllProblems(file: PsiFile, checkedDomains: Set<TextDomain>, allCheckers: List<TextChecker> = TextChecker.allCheckers()): List<TextProblem> {
  val texts = HighlightingUtil.getAllFileTexts(file.viewProvider)
    .filter { ProblemFilter.allIgnoringFilters(it).findAny().isEmpty }
  texts.forEach { text -> require(text.getUserData(EXTRACTOR_SOURCE) != null) { "Text should contain source element" } }
  if (texts.sumOf { it.length } > MAX_TEXT_LENGTH_IN_FILE) return emptyList()

  return getAllProblems(texts, checkedDomains, allCheckers)
}

private fun getAllProblems(texts: List<TextContent>, checkedDomains: Set<TextDomain>, allCheckers: List<TextChecker>): List<TextProblem> =
  buildProblemMap(allCheckers, texts, checkedDomains)
    .flatMap { (text, problems) ->
      TextProblemAggregator.aggregate(problems, text.toString(), toTokens(text).map { it.range }, false)
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

private fun buildAssociatedGrazieMapping(language: Language): Map<String, Rule> {
  val associatedGrazieRules = hashMapOf<String, Rule>()
  val ltPrefix = LangTool.globalIdPrefix(language)
  featuredSettings(language)
    .filterIsInstance<RuleSetting>()
    .map { it.rule }
    .forEach { grazieRule ->
      grazieRule.associatedLTRules.forEach { associatedLTRule ->
        associatedGrazieRules[ltPrefix + associatedLTRule.id] = grazieRule
      }
    }
  return associatedGrazieRules
}

fun Rule.isEnabledInState(state: GrazieConfig.State, domain: TextStyleDomain): Boolean {
  return if (this.isRuleEnabledByDefault(state.getTextStyle(domain), RuleIdeClient.INSTANCE)) {
    !state.isRuleDisabled(this.globalId(), domain)
  }
  else {
    state.isRuleEnabled(this.globalId(), domain)
  }
}

internal fun getGrazieTracker(file: PsiFile): ModificationTracker {
  return ModificationTracker {
    service<GrazieConfig>().modificationCount +
    DictionaryModificationTracker.getInstance(file.project).modificationCount +
    file.modificationStamp +
    PsiModificationTracker.getInstance(file.project).modificationCount
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
