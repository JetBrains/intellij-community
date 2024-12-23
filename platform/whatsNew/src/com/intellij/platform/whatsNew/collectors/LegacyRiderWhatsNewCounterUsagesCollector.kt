// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew.collectors

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionRuleValidator
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import kotlin.jvm.java

internal object LegacyRiderWhatsNewCounterUsagesCollector : CounterUsagesCollector() {
  private val eventLogGroup: EventLogGroup = EventLogGroup("rider.whatsnew.eap", 4)
  internal val opened = eventLogGroup.registerEvent("tab_opened", EventFields.Enum(("type"), OpenedType::class.java))
  internal val closed = eventLogGroup.registerEvent("tab_closed")
  internal val actionId = EventFields.StringValidatedByCustomRule("action_id", ActionRuleValidator::class.java)
  internal val perform = eventLogGroup.registerEvent("action_performed", actionId)
  internal val failed = eventLogGroup.registerEvent("action_failed", actionId, EventFields.Enum(("type"), ActionFailedReason::class.java))

  override fun getGroup(): EventLogGroup {
    return eventLogGroup
  }
}