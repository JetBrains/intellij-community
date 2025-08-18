package com.intellij.grazie.ide.fus

import ai.grazie.nlp.langs.Language
import com.intellij.grazie.ide.fus.TextContext.Companion.determineContext
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import java.time.Duration

@Suppress("UnstableApiUsage")
internal class GrazieProCounterUsagesCollector: CounterUsagesCollector() {
  override fun getGroup() = Companion.group

  companion object {
    internal val group = EventLogGroup("ai.assistant.grazie.pro.count", 5)

    private object CompletionEventFields {
      val language = EventFields.Enum<Language>("language") { it.iso.name.lowercase() }
    }

    private val definitionRequested = group.registerEvent(
      "definition.requested",
      CompletionEventFields.language,
      EventFields.Int("word_count")
    )

    private val definitionShown = group.registerEvent(
      "definition.shown",
      CompletionEventFields.language,
      EventFields.Count,
      EventFields.DurationMs
    )

    @JvmStatic
    fun reportDefinitionRequested(language: Language, wordCount: Int) {
      definitionRequested.log(language, wordCount)
    }

    @JvmStatic
    fun reportDefinitionShown(language: Language, definitionCount: Int, displayTime: Duration) {
      definitionShown.log(language, definitionCount, displayTime.toMillis())
    }

    private object RephraseEventFields {
      val language = EventFields.Enum<Language>("language") { it.iso.name.lowercase() }
      val sentenceLength = EventFields.Int("sentence_length")
      val rangeLength = EventFields.Int("range_length")
      val rangeWordCount = EventFields.Int("range_word_count")
      val rephraseLength = EventFields.Int("rephrase_length")
      val rephraseWordCount = EventFields.Int("rephrase_word_count")
      val suggestionCount = EventFields.Int("suggestion_count")
      val appliedRank = EventFields.Int("applied_rank")
    }

    private val rephraseRequested = group.registerVarargEvent(
      "rephrase.requested",
      RephraseEventFields.language,
      RephraseEventFields.sentenceLength,
      RephraseEventFields.rangeLength,
      RephraseEventFields.rangeWordCount
    )

    private val rephraseShownEmpty = group.registerVarargEvent(
      "rephrase.shown.empty",
      RephraseEventFields.language,
      RephraseEventFields.sentenceLength,
      RephraseEventFields.rangeLength,
      RephraseEventFields.rangeWordCount
    )

    private val rephraseRejected = group.registerEvent(
      "rephrase.rejected",
      RephraseEventFields.language,
      RephraseEventFields.suggestionCount
    )

    private val rephraseApplied = group.registerVarargEvent(
      "rephrase.applied",
      RephraseEventFields.language,
      RephraseEventFields.suggestionCount,
      RephraseEventFields.rephraseLength,
      RephraseEventFields.rephraseWordCount,
      RephraseEventFields.appliedRank
    )

    @JvmStatic
    fun reportRephraseRequested(language: Language, sentenceLength: Int, rangeLength: Int, rangeWordCount: Int) {
      rephraseRequested.log(
        RephraseEventFields.language.with(language),
        RephraseEventFields.sentenceLength.with(sentenceLength),
        RephraseEventFields.rangeLength.with(rangeLength),
        RephraseEventFields.rangeWordCount.with(rangeWordCount))
    }

    @JvmStatic
    fun reportRephraseEmpty(language: Language, sentenceLength: Int?, rangeLength: Int?, rangeWordCount: Int?) {
      rephraseShownEmpty.log(
        RephraseEventFields.language.with(language),
        RephraseEventFields.sentenceLength.with(sentenceLength ?: 0),
        RephraseEventFields.rangeLength.with(rangeLength ?: 0),
        RephraseEventFields.rangeWordCount.with(rangeWordCount ?: 0)
      )
    }

    @JvmStatic
    fun reportRephraseRejected(language: Language, suggestionCount: Int) {
      rephraseRejected.log(language, suggestionCount)
    }

    @JvmStatic
    fun reportRephraseApplied(language: Language, suggestionCount: Int, rephraseLength: Int, rephraseWordCount: Int, appliedRank: Int) {
      rephraseApplied.log(
        RephraseEventFields.language.with(language),
        RephraseEventFields.suggestionCount.with(suggestionCount),
        RephraseEventFields.rephraseLength.with(rephraseLength),
        RephraseEventFields.rephraseWordCount.with(rephraseWordCount),
        RephraseEventFields.appliedRank.with(appliedRank)
      )
    }


    private object TranslateEventFields {
      val fromLanguage = EventFields.Enum<Language>("from") { it.iso.name.lowercase() }
      val toLanguage = EventFields.Enum<Language>("to") { it.iso.name.lowercase() }
      val srcWordCount = EventFields.Int("src_word_count")
      val translationWordCount= EventFields.Int("translation_word_count")
    }

    private val translateRequested = group.registerVarargEvent(
      "translate.requested",
      TranslateEventFields.fromLanguage,
      TranslateEventFields.toLanguage,
      TranslateEventFields.srcWordCount
    )

    private val translateReplaced = group.registerVarargEvent(
      "translate.replaced",
      TranslateEventFields.fromLanguage,
      TranslateEventFields.toLanguage,
      TranslateEventFields.srcWordCount,
      TranslateEventFields.translationWordCount
    )

    @JvmStatic
    fun reportTranslateRequested(fromLanguage: Language, toLanguage: Language, srcWordCount: Int) {
      translateRequested.log(
        TranslateEventFields.fromLanguage.with(fromLanguage),
        TranslateEventFields.toLanguage.with(toLanguage),
        TranslateEventFields.srcWordCount.with(srcWordCount)
      )
    }

    @JvmStatic
    fun reportTranslateError(fromLanguage: Language, toLanguage: Language, srcWordCount: Int) {
      translateRequested.log(
        TranslateEventFields.fromLanguage.with(fromLanguage),
        TranslateEventFields.toLanguage.with(toLanguage),
        TranslateEventFields.srcWordCount.with(srcWordCount),
      )
    }

    @JvmStatic
    fun reportTranslateReplaced(fromLanguage: Language, toLanguage: Language, srcWordCount: Int, translationWordCount: Int) {
      translateReplaced.log(
        TranslateEventFields.fromLanguage.with(fromLanguage),
        TranslateEventFields.toLanguage.with(toLanguage),
        TranslateEventFields.srcWordCount.with(srcWordCount),
        TranslateEventFields.translationWordCount.with(translationWordCount)
      )
    }


    private object RuleIdFields {
      val RULE_FIELD = EventFields.StringValidatedByEnum("id", "grazie_rule_long_ids")
      val DOMAIN_FIELD = EventFields.Enum<TextContext>("domain")
      val TEXT_LANGUAGE_FIELD = EventFields.Enum<Language>("natural_language")
    }

    private val autoFixApplied = group.registerVarargEvent(
      "auto.fix.applied",
      RuleIdFields.RULE_FIELD,
      RuleIdFields.DOMAIN_FIELD,
      EventFields.Language,
      RuleIdFields.TEXT_LANGUAGE_FIELD,
      EventFields.PluginInfo,
    )

    private val autoFixUndone = group.registerVarargEvent(
      "auto.fix.undone",
      RuleIdFields.RULE_FIELD,
      RuleIdFields.DOMAIN_FIELD,
      EventFields.Language,
      RuleIdFields.TEXT_LANGUAGE_FIELD,
      EventFields.PluginInfo,
    )

    @JvmStatic
    fun reportAutoFixApplied(tracker: AcceptanceRateTracker) {
      autoFixApplied.log(listOf(
        RuleIdFields.RULE_FIELD.with(tracker.rule.globalId),
        RuleIdFields.DOMAIN_FIELD.with(determineContext(tracker)),
        EventFields.Language.with(tracker.programmingLanguage),
        RuleIdFields.TEXT_LANGUAGE_FIELD.with(tracker.textLanguage),
        EventFields.PluginInfo.with(getPluginInfo(tracker.rule.javaClass)),
      ))
    }

    @JvmStatic
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
}
