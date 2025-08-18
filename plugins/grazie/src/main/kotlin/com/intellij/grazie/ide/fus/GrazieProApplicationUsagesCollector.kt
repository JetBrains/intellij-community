package com.intellij.grazie.ide.fus

import ai.grazie.rules.settings.TextStyle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieConfig.State.Processing
import com.intellij.grazie.cloud.GrazieCloudConnector
import com.intellij.grazie.rule.RuleIdeClient
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector

@Suppress("UnstableApiUsage")
internal class GrazieProApplicationUsagesCollector : ApplicationUsagesCollector() {
  override fun getGroup(): EventLogGroup {
    return Companion.group
  }

  override fun getMetrics(): MutableSet<MetricEvent> {
    val metrics = HashSet<MetricEvent>()
    val state = GrazieConfig.get()
    // state.processing doesn't have a constant default value, so we always report it
    val connectionType = GrazieCloudConnector.EP_NAME.extensionList.firstNotNullOfOrNull { it.connectionType() } ?: Processing.Local
    metrics.add(processing.metric(connectionType))
    if (state.styleProfile != defaultState.styleProfile) {
      metrics.add(writingStyle.metric(state.textStyle.id.uppercase()))
    }
    if (state.autoFix != defaultState.autoFix) {
      metrics.add(autoFix.metric(state.autoFix))
    }
    return metrics
  }

  companion object {
    private val defaultState = GrazieConfig.get()

    private val group = EventLogGroup("ai.assistant.grazie.pro.state", 3)

    private val autoFix = group.registerEvent("settings.auto.fix", EventFields.Enabled)

    private val processing = group.registerEvent(
      "settings.processing",
      EventFields.Enum<Processing>("type") { it.name.uppercase() }
    )

    private val writingStyle = group.registerEvent(
      "settings.writing.style",
      EventFields.String("style", TextStyle.styles(RuleIdeClient.INSTANCE).map { it.id.uppercase() })
    )
  }
}
