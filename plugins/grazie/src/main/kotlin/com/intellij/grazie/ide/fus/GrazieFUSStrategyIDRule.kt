package com.intellij.grazie.ide.fus

import com.intellij.grazie.ide.language.LanguageGrammarChecking
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule

internal class GrazieFUSStrategyIDRule : CustomValidationRule() {
  private val defaultEnabledStrategies = setOf("nl.rubensten.texifyidea:Latex", "org.asciidoctor.intellij.asciidoc:AsciiDoc")

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    return if (data in defaultEnabledStrategies || data in LanguageGrammarChecking.getStrategies().map { it.getID() }.toSet()) {
      ValidationResultType.ACCEPTED
    }
    else {
      ValidationResultType.REJECTED
    }
  }

  override fun acceptRuleId(ruleId: String?) = ruleId == "grazie_strategy_id"
}