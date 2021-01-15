// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.fus

import com.intellij.grazie.detector.model.Language
import com.intellij.grazie.grammar.Typo
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger

internal object GrazieFUSCounter {
  fun languagesSuggested(languages: Collection<Language>, isEnabled: Boolean) {
    for (language in languages) {
      log("language.suggested") {
        addData("language", language.iso.toString())
        addData("enabled", isEnabled)
      }
    }
  }

  fun typoFound(typo: Typo) = log("typo.found") {
    addData("id", typo.info.rule.id)
    addData("fixes", typo.fixes.size)
    addProject(typo.location.element?.project)
  }

  private fun log(eventId: String, body: FeatureUsageData.() -> Unit) {
    FUCounterUsageLogger.getInstance().logEvent("grazie.count", eventId, FeatureUsageData().apply(body))
  }
}
