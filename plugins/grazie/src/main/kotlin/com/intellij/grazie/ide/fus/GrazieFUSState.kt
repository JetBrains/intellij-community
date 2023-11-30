// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.fus

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.config.CheckingContext
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.allRules
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.collectors.fus.LangCustomRuleValidator
import com.intellij.internal.statistic.collectors.fus.PluginInfoValidationRule
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo

private val GROUP = EventLogGroup("grazie.state", 7)
private val ENABLE_LANGUAGE = GROUP.registerEvent(
  "enabled.language",
  EventFields.Enum("value", LanguageISO::class.java) { it.name.lowercase() }
)

private val RULE = GROUP.registerEvent(
  "rule",
  EventFields.PluginInfo,
  EventFields.StringValidatedByCustomRule("id", PluginInfoValidationRule::class.java),
  EventFields.Enabled
)

private val DOCUMENTATION_FIELD = EventFields.StringValidatedByEnum("documentation", "state")

private val COMMENTS_FIELD = EventFields.StringValidatedByEnum("comments", "state")

private val LITERALS_FIELD = EventFields.StringValidatedByEnum("literals", "state")

private val COMMIT_FIELD = EventFields.StringValidatedByEnum("commit", "state")

private val USER_CHANGE_FIELD = EventFields.StringValidatedByEnum("userChange", "state")

private val LANGUAGE_FIELD = EventFields.StringValidatedByCustomRule("language", LangCustomRuleValidator::class.java)

private val CHECKING_CONTEXT = GROUP.registerVarargEvent(
  "checkingContext",
  LANGUAGE_FIELD,
  USER_CHANGE_FIELD,
  DOCUMENTATION_FIELD,
  COMMENTS_FIELD,
  LITERALS_FIELD,
  COMMIT_FIELD
)

internal class GrazieFUSState : ApplicationUsagesCollector() {
  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  override fun getMetrics(): Set<MetricEvent> {
    val metrics = HashSet<MetricEvent>()

    val state = GrazieConfig.get()

    for (lang in state.enabledLanguages) {
      metrics.add(ENABLE_LANGUAGE.metric(lang.iso))
    }

    val allRules by lazy { allRules().values.flatten().groupBy { it.globalId } }
    fun logRule(id: String, enabled: Boolean) {
      val rule = allRules[id]?.firstOrNull() ?: return
      metrics.add(RULE.metric(getPluginInfo(rule.javaClass), id, enabled))
    }

    state.userEnabledRules.forEach { logRule(it, enabled = true) }
    state.userDisabledRules.forEach { logRule(it, enabled = false) }

    val checkingContext = state.checkingContext
    for (id in checkingContext.disabledLanguages) {
      metrics.add(CHECKING_CONTEXT.metric(LANGUAGE_FIELD.with(id), USER_CHANGE_FIELD.with("disabled")))
    }
    for (id in checkingContext.enabledLanguages) {
      metrics.add(CHECKING_CONTEXT.metric(LANGUAGE_FIELD.with(id), USER_CHANGE_FIELD.with("enabled")))
    }

    val defaults = CheckingContext()
    fun checkDomain(name: StringEventField, isEnabled: (CheckingContext) -> Boolean) {
      if (isEnabled(defaults) != isEnabled(checkingContext)) {
        metrics.add(CHECKING_CONTEXT.metric(name.with(if (isEnabled(checkingContext)) "enabled" else "disabled")))
      }
    }

    checkDomain(DOCUMENTATION_FIELD) { it.isCheckInDocumentationEnabled }
    checkDomain(COMMENTS_FIELD) { it.isCheckInCommentsEnabled }
    checkDomain(LITERALS_FIELD) { it.isCheckInStringLiteralsEnabled }
    checkDomain(COMMIT_FIELD) { it.isCheckInCommitMessagesEnabled }

    return metrics
  }
}
