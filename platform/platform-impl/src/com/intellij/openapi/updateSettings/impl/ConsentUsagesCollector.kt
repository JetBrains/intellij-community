// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.gdpr.Consent
import com.intellij.ide.gdpr.ConsentOptions
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import java.util.function.Predicate

internal class ConsentUsagesCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("user.consents", 1)

  private val USAGE_STATS = EventFields.Boolean("usage_stats", "User accepted sending anonymous usage statistics")
  private val AI_DATA = EventFields.Boolean("ai_data", "User accepted AI data collection and use policy")
  private val EA_AUTO_REPORT = EventFields.Boolean("ea_auto_report", "User accepted automatic error report sending")
  private val EAP_FEEDBACK = EventFields.Boolean("eap_feedback", "User accepted sending EAP feedback")

  private val STATE = GROUP.registerVarargEvent("state", USAGE_STATS, AI_DATA, EA_AUTO_REPORT, EAP_FEEDBACK)

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    return setOf(
      STATE.metric(
        USAGE_STATS with accepted(ConsentOptions.condUsageStatsConsent()),
        AI_DATA with accepted(ConsentOptions.condAiDataCollectionConsent()),
        EA_AUTO_REPORT with accepted(ConsentOptions.condEAAutoReportConsent()),
        EAP_FEEDBACK with accepted(ConsentOptions.condEAPFeedbackConsent()),
      )
    )
  }

  private fun accepted(predicate: Predicate<Consent>): Boolean {
    val (consents, needsReconfirm) = ConsentOptions.getInstance().getConsents(predicate)
    val consent = consents.firstOrNull() ?: return false
    return consent.isAccepted && !needsReconfirm
  }
}
