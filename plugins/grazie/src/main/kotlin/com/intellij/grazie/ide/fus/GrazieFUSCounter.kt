// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.fus

import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.text.Rule
import com.intellij.grazie.text.TextProblem
import com.intellij.internal.statistic.collectors.fus.PluginInfoValidationRule
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.project.Project

private val actionInfo = listOf("accept.suggestion",
                                "add.exception",
                                "rule.settings:canceled",
                                "rule.settings:unmodified",
                                "rule.settings:changes:languages,domains,rules",
                                "rule.settings:changes:languages,rules",
                                "rule.settings:changes:languages,domains",
                                "rule.settings:changes:domains,rules",
                                "rule.settings:changes:languages",
                                "rule.settings:changes:rules",
                                "rule.settings:changes:domains",
                                "rule.settings:changes:unclassified")

private val GROUP = EventLogGroup("grazie.count", 7)

private val RULE_FIELD = EventFields.StringValidatedByCustomRule("id", PluginInfoValidationRule::class.java)
private val FIXES_FIELD = EventFields.Int("fixes")
private val ACTION_INFO_FIELD = EventFields.String("info", actionInfo)

private val languageSuggestedEvent = GROUP.registerEvent("language.suggested",
                                                         EventFields.Enum("language", LanguageISO::class.java),
                                                         EventFields.Enabled)
private val typoFoundEvent = GROUP.registerEvent("typo.found",
                                                 RULE_FIELD,
                                                 FIXES_FIELD,
                                                 EventFields.PluginInfo)
private val quickFixInvokedEvent = GROUP.registerEvent("quick.fix.invoked",
                                                       RULE_FIELD,
                                                       ACTION_INFO_FIELD,
                                                       EventFields.PluginInfo)

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

  fun quickFixInvoked(rule: Rule, project: Project, actionInfo: String) = quickFixInvokedEvent.log(project,
                                                                                                   rule.globalId,
                                                                                                   actionInfo,
                                                                                                   getPluginInfo(rule.javaClass))

}