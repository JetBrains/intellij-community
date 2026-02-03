// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.groovy.util.LibrariesUtil

class GroovyLanguageVersionCollector : ProjectUsagesCollector() {
  private val group = EventLogGroup("groovy.language", 1)

  private val groovySdkVersion = group.registerEvent(
    "GROOVY_SDK_VERSION",
    EventFields.Version
  )

  override fun getGroup(): EventLogGroup = group

  override fun requiresReadAccess(): Boolean = true

  override fun requiresSmartMode(): Boolean = true

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val metricEventSet = mutableSetOf<MetricEvent>()
    LibrariesUtil.findAllJarsWithClass(project, LibrariesUtil.SOME_GROOVY_CLASS).forEach { jar ->
      jar.parent?.path?.let { path ->
        val version = GroovyConfigUtils.getInstance().getSDKVersionOrNull(path)
        metricEventSet.add(groovySdkVersion.metric(version))
      }
    }

    return metricEventSet
  }
}