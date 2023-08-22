// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.vcs.commit.NonModalCommitUsagesCollector

class VcsApplicationOptionsUsagesCollector : ApplicationUsagesCollector() {
  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  override fun getMetrics(): Set<MetricEvent> = NonModalCommitUsagesCollector.getMetrics()


  companion object {
    internal val GROUP = EventLogGroup("vcs.application.configuration", 4)
    internal val NON_MODAL_COMMIT = GROUP.registerVarargEvent("non.modal.commit", EventFields.Enabled)
  }
}