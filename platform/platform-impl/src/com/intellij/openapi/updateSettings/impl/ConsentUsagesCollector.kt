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
  private val GROUP = EventLogGroup("user.consents", 3)

  private val CONSENT_ID = EventFields.Enum<ConsentId>("id", "Tracked user consent") { it.fieldValue }
  private val ACCEPTED = EventFields.Boolean("accepted", "User accepted the consent")
  private val NEEDS_RECONFIRM = EventFields.Boolean("needs_reconfirm", "The consent needs reconfirmation")

  private val STATE = GROUP.registerEvent("state", CONSENT_ID, ACCEPTED, NEEDS_RECONFIRM)

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    return ConsentId.entries.mapNotNullTo(LinkedHashSet()) { id ->
      val (consents, needsReconfirm) = ConsentOptions.getInstance().getConsents(id.predicate())
      val consent = consents.firstOrNull() ?: return@mapNotNullTo null
      STATE.metric(id, consent.isAccepted, needsReconfirm)
    }
  }

  private enum class ConsentId(val fieldValue: String, val predicate: () -> Predicate<Consent>) {
    USAGE_STATS("usage_stats", ConsentOptions::condUsageStatsConsent),
    AI_DATA("ai_data", ConsentOptions::condAiDataCollectionConsent),
    EA_AUTO_REPORT("ea_auto_report", ConsentOptions::condEAAutoReportConsent),
    EAP_FEEDBACK("eap_feedback", ConsentOptions::condEAPFeedbackConsent),
  }
}
