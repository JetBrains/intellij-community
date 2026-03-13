package com.intellij.grazie.utils

import ai.grazie.detector.ChainLanguageDetector
import ai.grazie.detector.DefaultLanguageDetectors
import ai.grazie.gec.model.problem.Problem
import ai.grazie.gec.model.problem.ProblemHighlighting
import ai.grazie.gec.model.problem.SentenceWithProblems
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
import com.intellij.grazie.ide.ui.configurable.StyleConfigurable.Companion.ruleEngineLanguages
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.mlec.LanguageHolder
import com.intellij.grazie.mlec.MlecChecker
import com.intellij.grazie.rule.RuleIdeClient
import com.intellij.grazie.rule.SentenceBatcher
import com.intellij.grazie.rule.SentenceBatcher.Companion.runWithSentenceBatcher
import com.intellij.grazie.rule.SentenceTokenizer.tokenize
import com.intellij.grazie.spellcheck.SpellingTextChecker
import com.intellij.grazie.text.TextChecker
import com.intellij.grazie.text.TextChecker.ProofreadingContext
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContentImpl
import com.intellij.grazie.utils.HighlightingUtil.findInstalledLang
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.containers.CollectionFactory.createConcurrentSoftValueMap
import java.util.concurrent.ConcurrentHashMap
import ai.grazie.text.TextRange as GrazieTextRange

private val affectedGlobalRules = createConcurrentSoftValueMap<Language, Set<String>>()
private val associatedGrazieRules = ConcurrentHashMap<Language, Map<String, Rule>>()

fun getAssociatedGrazieRule(rule: com.intellij.grazie.text.Rule): Rule? {
  if (rule.language !in ruleEngineLanguages) return null
  if (rule.globalId == MlecChecker.Constants.enMissingArticle.globalId) {
    return featuredSettings(Language.ENGLISH).asSequence()
      .filterIsInstance<RuleSetting>()
      .map { it.rule }
      .find { it.id == "Grammar.MISSING_ARTICLE" }!!
  }
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
fun getLanguageIfAvailable(text: TextContent, strippedOffset: Int? = null): Language? {
  val offset = strippedOffset ?: HighlightingUtil.stripPrefix(text)
  // Rider `ExternalTextContent` doesn't support view providers, hence batch detection is not available
  if (text is TextContentImpl && Registry.`is`("grazie.batch.language.detector", false)) {
    return BatchLangDetector.getLanguage(text, offset)?.takeIf { findInstalledLang(it) != null }
  } else {
    return getLanguageIfAvailable(text.toString().substring(offset))
  }
}

@Deprecated("Use getLanguageIfAvailable(TextContent) instead")
fun getLanguageIfAvailable(text: String): Language? {
  return LangDetector.getLanguage(text)?.takeIf { findInstalledLang(it) != null }
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
  }
}

fun ProofreadingContext.shouldCheckGrammarStyle(): Boolean = this.language != UNKNOWN && findInstalledLang(this.language) != null

internal fun TextChecker.isSpelling(): Boolean = this is SpellingTextChecker
internal fun TextChecker.isGrammar(): Boolean = this !is SpellingTextChecker

suspend fun <T : LanguageHolder<SentenceBatcher<SentenceWithProblems>>> getProblems(context: ProofreadingContext, parserClass: Class<T>): List<Problem>? {
  val stripPrefixLength = context.stripPrefix.length
  val subText = context.text.subText(TextRange(stripPrefixLength, context.text.length)) ?: return emptyList()
  val sentences = tokenize(subText)
  val parsed = runWithSentenceBatcher(sentences, context.language, context.text.containingFile.viewProvider, parserClass)
  if (parsed == null) return null
  if (parsed.isEmpty()) return emptyList()

  val result = ArrayList<Problem>()
  for (sentence in sentences) {
    val corrections = parsed[sentence.swe()]?.problems ?: continue
    val start = sentence.start + stripPrefixLength
    if (!context.text.hasUnknownFragmentsIn(TextRange.from(start, sentence.text.trimEnd().length))) {
      corrections.forEach {
        result.add(it.withOffset(start))
      }
    }
  }
  return result
}

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