// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.fus

import com.intellij.grazie.detector.model.Language
import com.intellij.grazie.text.Rule
import com.intellij.grazie.text.TextProblem
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.project.Project

internal object GrazieFUSCounter {
  fun languagesSuggested(languages: Collection<Language>, isEnabled: Boolean) {
    for (language in languages) {
      log("language.suggested") {
        addData("language", language.iso.toString())
        addData("enabled", isEnabled)
      }
    }
  }

  fun typoFound(problem: TextProblem) {
    log("typo.found") {
      addData("id", problem.rule.globalId)
      addPluginInfo(getPluginInfo(problem.rule.javaClass))
      addData("fixes", problem.suggestions.size)
      addProject(problem.text.containingFile.project)
    }
  }

  fun quickFixInvoked(rule: Rule, project: Project, actionInfo: String) {
    log("quick.fix.invoked") {
      addData("id", rule.globalId)
      addData("info", actionInfo)
      addPluginInfo(getPluginInfo(rule.javaClass))
      addProject(project)
    }
  }

  private fun log(eventId: String, body: FeatureUsageData.() -> Unit) {
    FUCounterUsageLogger.getInstance().logEvent("grazie.count", eventId, FeatureUsageData().apply(body))
  }
}
