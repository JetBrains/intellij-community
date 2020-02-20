// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.fus

import com.intellij.grazie.grammar.Typo
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import tanvd.grazie.langdetect.model.Language

object GrazieFUCounterCollector {
  fun languageDetected(lang: Language?) = log("language.detected") {
    addData("language", lang?.iso?.toString() ?: "")
  }

  fun typoFound(typo: Typo) = log("typo.found") {
    addData("id", typo.info.rule.id)
    addData("fixes", typo.fixes.size)
  }

  fun quickfixApplied(ruleId: String, cancelled: Boolean) = log("quickfix.applied") {
    addData("id", ruleId)
    addData("cancelled", cancelled)
  }

  private fun log(eventId: String, body: FeatureUsageData.() -> Unit) = FUCounterUsageLogger.getInstance()
    .logEvent("grazi.count", eventId, FeatureUsageData().apply(body))
}
