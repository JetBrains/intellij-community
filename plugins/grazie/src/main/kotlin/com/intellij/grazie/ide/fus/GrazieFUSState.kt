// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.fus

import com.intellij.grazie.GrazieConfig
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.lang.Language

internal class GrazieFUSState : ApplicationUsagesCollector() {
  override fun getGroupId(): String = "grazie.state"
  override fun getVersion(): Int = 1

  override fun getMetrics(): Set<MetricEvent> {
    val metrics = HashSet<MetricEvent>()

    val state = GrazieConfig.get()

    for (lang in state.enabledLanguages) {
      metrics.add(newMetric("enabled.language", lang.iso))
    }

    for (id in state.userEnabledRules) {
      metrics.add(newMetric("rule", FeatureUsageData().addData("id", id).addData("enabled", true)))
    }
    for (id in state.userDisabledRules) {
      metrics.add(newMetric("rule", FeatureUsageData().addData("id", id).addData("enabled", false)))
    }


    for (id in state.checkingContext.disabledLanguages) {
      val language = Language.findLanguageByID(id) ?: continue
      if (!getPluginInfo(language.javaClass).isSafeToReport()) continue

      metrics.add(newMetric("checkingContext", FeatureUsageData().addData("disabled_language", id)))
    }

    return metrics
  }
}
