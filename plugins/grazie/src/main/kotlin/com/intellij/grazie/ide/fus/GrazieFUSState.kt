// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.fus

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.allRules
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo

internal class GrazieFUSState : ApplicationUsagesCollector() {
  override fun getGroupId(): String = "grazie.state"
  override fun getVersion(): Int = 2

  override fun getMetrics(): Set<MetricEvent> {
    val metrics = HashSet<MetricEvent>()

    val state = GrazieConfig.get()

    for (lang in state.enabledLanguages) {
      metrics.add(newMetric("enabled.language", lang.iso))
    }

    val allRules by lazy { allRules().values.flatten().groupBy { it.globalId } }
    fun logRule(id: String, enabled: Boolean) {
      val rule = allRules[id]?.firstOrNull() ?: return
      metrics.add(newMetric("rule", FeatureUsageData()
        .addPluginInfo(getPluginInfo(rule.javaClass))
        .addData("id", id)
        .addData("enabled", enabled)))
    }

    state.userEnabledRules.forEach { logRule(it, enabled = true) }
    state.userDisabledRules.forEach { logRule(it, enabled = false) }

    for (id in state.checkingContext.disabledLanguages) {
      metrics.add(newMetric("checkingContext", FeatureUsageData().addData("disabled_language", id)))
    }
    for (id in state.checkingContext.enabledLanguages) {
      metrics.add(newMetric("checkingContext", FeatureUsageData().addData("enabled_language", id)))
    }

    return metrics
  }
}
