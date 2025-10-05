package com.intellij.grazie.utils

import ai.grazie.gec.model.problem.ProblemHighlighting
import ai.grazie.nlp.langs.Language
import ai.grazie.rules.Rule
import ai.grazie.rules.settings.RuleSetting
import ai.grazie.rules.settings.Setting
import ai.grazie.rules.toolkit.LanguageToolkit
import com.intellij.grazie.detection.LangDetector
import com.intellij.grazie.ide.ui.configurable.StyleConfigurable.Companion.ruleEngineLanguages
import com.intellij.grazie.rule.RuleIdeClient
import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.ContainerUtil.createConcurrentSoftValueMap
import ai.grazie.text.TextRange as GrazieTextRange

private val affectedGlobalRules = createConcurrentSoftValueMap<Language, Set<String>>()

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

val ProblemHighlighting.underline: TextRange?
  get() = GrazieTextRange.coveringIde(this.always)