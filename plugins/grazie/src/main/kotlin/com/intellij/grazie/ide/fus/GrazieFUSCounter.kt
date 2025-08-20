// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.fus

import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.ide.fus.TextContext.Companion.determineContext
import com.intellij.grazie.text.Rule
import com.intellij.grazie.text.TextProblem
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.project.Project
import java.time.Duration

private val actionInfo = listOf("rule.settings:canceled",
                                "rule.settings:unmodified",
                                "rule.settings:changes:languages,domains,rules",
                                "rule.settings:changes:languages,rules",
                                "rule.settings:changes:languages,domains",
                                "rule.settings:changes:domains,rules",
                                "rule.settings:changes:languages",
                                "rule.settings:changes:rules",
                                "rule.settings:changes:domains",
                                "rule.settings:changes:unclassified")

private val GROUP = EventLogGroup("grazie.count", 10)

private val LANGUAGE_FIELD = EventFields.Enum<Language>("language") { it.iso.name.lowercase() }
private val RULE_FIELD = EventFields.StringValidatedByEnum("id", "grazie_rule_long_ids")
private val FIXES_FIELD = EventFields.Int("fixes")
private val ACTION_INFO_FIELD = EventFields.String("info", actionInfo)
private val DOMAIN_FIELD = EventFields.Enum<TextContext>("domain")
private val TEXT_LANGUAGE_FIELD = EventFields.Enum<Language>("natural_language")
private val INDEX_FIELD = EventFields.Int("index")
private val TOTAL_FIELD = EventFields.Int("total")

private val languageSuggestedEvent = GROUP.registerEvent("language.suggested",
                                                         EventFields.Enum("language", LanguageISO::class.java),
                                                         EventFields.Enabled)
private val typoFoundEvent = GROUP.registerEvent("typo.found",
                                                 RULE_FIELD,
                                                 FIXES_FIELD,
                                                 EventFields.PluginInfo)
private val addExceptionEvent = GROUP.registerVarargEvent("exception.added",
                                                          RULE_FIELD,
                                                          EventFields.PluginInfo,
                                                          DOMAIN_FIELD,
                                                          TEXT_LANGUAGE_FIELD,
                                                          EventFields.Language)

private val acceptSuggestionEvent = GROUP.registerVarargEvent("suggestion.accepted",
                                                              RULE_FIELD,
                                                              EventFields.PluginInfo,
                                                              INDEX_FIELD,
                                                              TOTAL_FIELD,
                                                              TEXT_LANGUAGE_FIELD,
                                                              DOMAIN_FIELD,
                                                              EventFields.Language)

private val updateSettingsEvent = GROUP.registerVarargEvent("settings.updated",
                                                            RULE_FIELD,
                                                            ACTION_INFO_FIELD,
                                                            EventFields.PluginInfo)

private val suggestionShownEvent = GROUP.registerVarargEvent("suggestion.shown",
                                                             RULE_FIELD,
                                                             DOMAIN_FIELD,
                                                             EventFields.Language,
                                                             TEXT_LANGUAGE_FIELD,
                                                             EventFields.PluginInfo)

//Ex. JB AIA Grazie Pro events
private val definitionRequested = GROUP.registerEvent(
  "definition.requested",
  LANGUAGE_FIELD,
  EventFields.Int("word_count")
)

private val definitionShown = GROUP.registerEvent(
  "definition.shown",
  LANGUAGE_FIELD,
  EventFields.Count,
  EventFields.DurationMs
)

private object RephraseEventFields {
  val sentenceLength = EventFields.Int("sentence_length")
  val rangeLength = EventFields.Int("range_length")
  val rangeWordCount = EventFields.Int("range_word_count")
  val rephraseLength = EventFields.Int("rephrase_length")
  val rephraseWordCount = EventFields.Int("rephrase_word_count")
  val suggestionCount = EventFields.Int("suggestion_count")
  val appliedRank = EventFields.Int("applied_rank")
}

private val rephraseRequested = GROUP.registerVarargEvent(
  "rephrase.requested",
  LANGUAGE_FIELD,
  RephraseEventFields.sentenceLength,
  RephraseEventFields.rangeLength,
  RephraseEventFields.rangeWordCount
)

private val rephraseShownEmpty = GROUP.registerVarargEvent(
  "rephrase.shown.empty",
  LANGUAGE_FIELD,
  RephraseEventFields.sentenceLength,
  RephraseEventFields.rangeLength,
  RephraseEventFields.rangeWordCount
)

private val rephraseRejected = GROUP.registerEvent(
  "rephrase.rejected",
  LANGUAGE_FIELD,
  RephraseEventFields.suggestionCount
)

private val rephraseApplied = GROUP.registerVarargEvent(
  "rephrase.applied",
  LANGUAGE_FIELD,
  RephraseEventFields.suggestionCount,
  RephraseEventFields.rephraseLength,
  RephraseEventFields.rephraseWordCount,
  RephraseEventFields.appliedRank
)

private object TranslateEventFields {
  val fromLanguage = EventFields.Enum<Language>("from") { it.iso.name.lowercase() }
  val toLanguage = EventFields.Enum<Language>("to") { it.iso.name.lowercase() }
  val srcWordCount = EventFields.Int("src_word_count")
  val translationWordCount= EventFields.Int("translation_word_count")
}

private val translateRequested = GROUP.registerVarargEvent(
  "translate.requested",
  TranslateEventFields.fromLanguage,
  TranslateEventFields.toLanguage,
  TranslateEventFields.srcWordCount
)

private val translateReplaced = GROUP.registerVarargEvent(
  "translate.replaced",
  TranslateEventFields.fromLanguage,
  TranslateEventFields.toLanguage,
  TranslateEventFields.srcWordCount,
  TranslateEventFields.translationWordCount
)

private object RuleIdFields {
  val RULE_FIELD = EventFields.StringValidatedByEnum("id", "grazie_rule_long_ids")
  val DOMAIN_FIELD = EventFields.Enum<TextContext>("domain")
  val TEXT_LANGUAGE_FIELD = EventFields.Enum<Language>("natural_language")
}

private val autoFixApplied = GROUP.registerVarargEvent(
  "auto.fix.applied",
  RuleIdFields.RULE_FIELD,
  RuleIdFields.DOMAIN_FIELD,
  EventFields.Language,
  RuleIdFields.TEXT_LANGUAGE_FIELD,
  EventFields.PluginInfo,
)

private val autoFixUndone = GROUP.registerVarargEvent(
  "auto.fix.undone",
  RuleIdFields.RULE_FIELD,
  RuleIdFields.DOMAIN_FIELD,
  EventFields.Language,
  RuleIdFields.TEXT_LANGUAGE_FIELD,
  EventFields.PluginInfo,
)

object GrazieFUSCounter : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  fun languagesSuggested(languages: Collection<Language>, isEnabled: Boolean) {
    for (language in languages) {
      languageSuggestedEvent.log(language.iso, isEnabled)
    }
  }

  fun typoFound(problem: TextProblem) = typoFoundEvent.log(problem.text.containingFile.project,
                                                           problem.rule.globalId,
                                                           problem.suggestions.size,
                                                           getPluginInfo(problem.rule.javaClass))

  fun exceptionAdded(project: Project, tracker: AcceptanceRateTracker) {
    val args = mutableListOf(
      RULE_FIELD.with(tracker.rule.globalId),
      EventFields.PluginInfo.with(getPluginInfo(tracker.rule.javaClass)),
      DOMAIN_FIELD.with(determineContext(tracker)),
      TEXT_LANGUAGE_FIELD.with(tracker.textLanguage),
      EventFields.Language.with(tracker.programmingLanguage)
    )
    addExceptionEvent.log(project, args)
  }

  fun suggestionAccepted(project: Project, tracker: AcceptanceRateTracker, index: Int, total: Int) {
    val args = mutableListOf(
      RULE_FIELD.with(tracker.rule.globalId),
      EventFields.PluginInfo.with(getPluginInfo(tracker.rule.javaClass)),
      DOMAIN_FIELD.with(determineContext(tracker)),
      TEXT_LANGUAGE_FIELD.with(tracker.textLanguage),
      EventFields.Language.with(tracker.programmingLanguage),
      INDEX_FIELD.with(index),
      TOTAL_FIELD.with(total),
    )
    acceptSuggestionEvent.log(project, args)
  }

  fun settingsUpdated(actionInfo: String, rule: Rule, project: Project) {
    updateSettingsEvent.log(project, listOf(
      RULE_FIELD.with(rule.globalId),
      ACTION_INFO_FIELD.with(actionInfo),
      EventFields.PluginInfo.with(getPluginInfo(rule.javaClass)),
    ))
  }

  fun suggestionShown(project: Project, tracker: AcceptanceRateTracker) {
    suggestionShownEvent.log(project, listOf(
      RULE_FIELD.with(tracker.rule.globalId),
      DOMAIN_FIELD.with(determineContext(tracker)),
      EventFields.Language.with(tracker.programmingLanguage),
      TEXT_LANGUAGE_FIELD.with(tracker.textLanguage),
      EventFields.PluginInfo.with(getPluginInfo(tracker.rule.javaClass)),
    ))
  }

  fun reportDefinitionRequested(language: Language, wordCount: Int) {
    definitionRequested.log(language, wordCount)
  }

  fun reportDefinitionShown(language: Language, definitionCount: Int, displayTime: Duration) {
    definitionShown.log(language, definitionCount, displayTime.toMillis())
  }

  fun reportRephraseRequested(language: Language, sentenceLength: Int, rangeLength: Int, rangeWordCount: Int) {
    rephraseRequested.log(
      LANGUAGE_FIELD.with(language),
      RephraseEventFields.sentenceLength.with(sentenceLength),
      RephraseEventFields.rangeLength.with(rangeLength),
      RephraseEventFields.rangeWordCount.with(rangeWordCount))
  }

  fun reportRephraseEmpty(language: Language, sentenceLength: Int?, rangeLength: Int?, rangeWordCount: Int?) {
    rephraseShownEmpty.log(
      LANGUAGE_FIELD.with(language),
      RephraseEventFields.sentenceLength.with(sentenceLength ?: 0),
      RephraseEventFields.rangeLength.with(rangeLength ?: 0),
      RephraseEventFields.rangeWordCount.with(rangeWordCount ?: 0)
    )
  }

  fun reportRephraseRejected(language: Language, suggestionCount: Int) {
    rephraseRejected.log(language, suggestionCount)
  }

  fun reportRephraseApplied(language: Language, suggestionCount: Int, rephraseLength: Int, rephraseWordCount: Int, appliedRank: Int) {
    rephraseApplied.log(
      LANGUAGE_FIELD.with(language),
      RephraseEventFields.suggestionCount.with(suggestionCount),
      RephraseEventFields.rephraseLength.with(rephraseLength),
      RephraseEventFields.rephraseWordCount.with(rephraseWordCount),
      RephraseEventFields.appliedRank.with(appliedRank)
    )
  }

  fun reportTranslateRequested(fromLanguage: Language, toLanguage: Language, srcWordCount: Int) {
    translateRequested.log(
      TranslateEventFields.fromLanguage.with(fromLanguage),
      TranslateEventFields.toLanguage.with(toLanguage),
      TranslateEventFields.srcWordCount.with(srcWordCount)
    )
  }

  fun reportTranslateError(fromLanguage: Language, toLanguage: Language, srcWordCount: Int) {
    translateRequested.log(
      TranslateEventFields.fromLanguage.with(fromLanguage),
      TranslateEventFields.toLanguage.with(toLanguage),
      TranslateEventFields.srcWordCount.with(srcWordCount),
    )
  }

  fun reportTranslateReplaced(fromLanguage: Language, toLanguage: Language, srcWordCount: Int, translationWordCount: Int) {
    translateReplaced.log(
      TranslateEventFields.fromLanguage.with(fromLanguage),
      TranslateEventFields.toLanguage.with(toLanguage),
      TranslateEventFields.srcWordCount.with(srcWordCount),
      TranslateEventFields.translationWordCount.with(translationWordCount)
    )
  }

  fun reportAutoFixApplied(tracker: AcceptanceRateTracker) {
    autoFixApplied.log(listOf(
      RuleIdFields.RULE_FIELD.with(tracker.rule.globalId),
      RuleIdFields.DOMAIN_FIELD.with(determineContext(tracker)),
      EventFields.Language.with(tracker.programmingLanguage),
      RuleIdFields.TEXT_LANGUAGE_FIELD.with(tracker.textLanguage),
      EventFields.PluginInfo.with(getPluginInfo(tracker.rule.javaClass)),
    ))
  }

  fun reportAutoFixUndone(tracker: AcceptanceRateTracker) {
    autoFixUndone.log(listOf(
      RuleIdFields.RULE_FIELD.with(tracker.rule.globalId),
      RuleIdFields.DOMAIN_FIELD.with(determineContext(tracker)),
      EventFields.Language.with(tracker.programmingLanguage),
      RuleIdFields.TEXT_LANGUAGE_FIELD.with(tracker.textLanguage),
      EventFields.PluginInfo.with(getPluginInfo(tracker.rule.javaClass)),
    ))
  }
}

enum class TextContext {
  /** String literals of a programming language  */
  LITERALS,

  /** Generic comments of a programming language, excluding doc comments  */
  COMMENTS,

  /** In-code documentation (JavaDocs, Python DocStrings, etc.)  */
  DOCUMENTATION,

  /** Plain text (e.g. txt, Markdown, HTML, LaTeX) and UI strings  */
  PLAIN_TEXT,

  /** Commit message */
  COMMIT_MESSAGE;

  companion object {
    @JvmStatic
    fun determineContext(tracker: AcceptanceRateTracker): TextContext {
      if (tracker.isCommitMessage) {
        return COMMIT_MESSAGE
      }
      return TextContext.valueOf(tracker.domain.name)
    }
  }
}