package com.intellij.grazie.utils

import ai.grazie.gec.model.problem.ProblemHighlighting
import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.Language.UNKNOWN
import ai.grazie.rules.Rule
import ai.grazie.rules.settings.RuleSetting
import ai.grazie.rules.settings.Setting
import ai.grazie.rules.toolkit.LanguageToolkit
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.detection.LangDetector
import com.intellij.grazie.ide.ui.configurable.StyleConfigurable.Companion.ruleEngineLanguages
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.rule.RuleIdeClient
import com.intellij.grazie.text.TextChecker
import com.intellij.grazie.text.TextChecker.ProofreadingContext
import com.intellij.grazie.text.TextContent
import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.CollectionFactory.createConcurrentSoftValueMap
import ai.grazie.text.TextRange as GrazieTextRange

private val affectedGlobalRules = createConcurrentSoftValueMap<Language, Set<String>>()
private val associatedGrazieRules = buildAssociatedGrazieMapping()

fun getAssociatedGrazieRule(ruleId: String): Rule? = associatedGrazieRules[ruleId]

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

fun getLanguageIfAvailable(text: String): Language? {
  return LangDetector.getLanguage(text)?.takeIf { HighlightingUtil.findInstalledLang(it) != null }
}

fun GrazieTextRange.Companion.coveringIde(ranges: Array<GrazieTextRange>): TextRange? {
  if (ranges.isEmpty()) return null
  return TextRange(ranges.minOf { it.start }, ranges.maxOf { it.endExclusive })
}

fun TextContent.toProofreadingContext(): ProofreadingContext {
  val content = this
  val stripPrefixLength = HighlightingUtil.stripPrefix(content)
  val language = LangDetector.getLanguage(content.toString().substring(stripPrefixLength)) ?: UNKNOWN
  return object : ProofreadingContext {
    override fun getText(): TextContent = content
    override fun getLanguage(): Language = language
    override fun getStripPrefix(): String = content.toString().substring(0, stripPrefixLength)
  }
}

val ProblemHighlighting.underline: TextRange?
  get() = GrazieTextRange.coveringIde(this.always)

private fun buildAssociatedGrazieMapping(): Map<String, Rule> {
  val associatedGrazieRules = hashMapOf<String, Rule>()
  ruleEngineLanguages.forEach { language ->
    val ltPrefix = LangTool.globalIdPrefix(language)
    featuredSettings(language)
      .filterIsInstance<RuleSetting>()
      .map { it.rule }
      .forEach { grazieRule ->
        grazieRule.associatedLTRules.forEach { associatedLTRule ->
          associatedGrazieRules[ltPrefix + associatedLTRule.id] = grazieRule
        }
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