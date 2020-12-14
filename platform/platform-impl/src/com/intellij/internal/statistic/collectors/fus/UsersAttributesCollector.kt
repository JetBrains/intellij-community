// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.validator.rules.impl.TestModeValidationRule
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.util.text.StringUtil

class UsersAttributesCollector : ApplicationUsagesCollector() {
  companion object {
    val GROUP = EventLogGroup("event.log.user.info", 1)
    val TEST_MODE_ENABLED = GROUP.registerEvent("statistics.test.mode.enabled")
    val TEAMCITY_DETECTED = GROUP.registerEvent("team.city.version.detected")
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  override fun getMetrics(): Set<MetricEvent> {
    val result = hashSetOf<MetricEvent>()
    if (TestModeValidationRule.isTestModeEnabled()) {
      result.add(TEST_MODE_ENABLED.metric())
    }
    if (StringUtil.isNotEmpty(System.getenv("TEAMCITY_VERSION"))) {
      result.add(TEAMCITY_DETECTED.metric())
    }
    return result
  }
}