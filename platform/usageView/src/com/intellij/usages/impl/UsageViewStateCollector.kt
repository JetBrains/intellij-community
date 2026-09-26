// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.usages.UsageViewProjectProperties
import org.jetbrains.annotations.ApiStatus

internal class UsageViewStateCollector : ProjectUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("usage.view.state", 1)
  private val PREVIEW_SOURCE_EVENT = GROUP.registerEvent("preview.source", EventFields.Enabled)

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val properties = UsageViewProjectProperties.getInstance(project)
    return setOf(
      PREVIEW_SOURCE_EVENT.metric(properties.isPreviewSource)
    )
  }
}
