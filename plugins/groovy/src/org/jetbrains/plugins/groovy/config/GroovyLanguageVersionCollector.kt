// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.groovy.util.LibrariesUtil

internal class GroovyLanguageVersionCollector : ProjectUsagesCollector() {
  private val GROUP = EventLogGroup("groovy.language", 1)
  private val VERSION_EVENT = GROUP.registerEvent("GROOVY_SDK_VERSION", EventFields.Version)

  override fun getGroup(): EventLogGroup = GROUP

  override suspend fun collect(project: Project): Set<MetricEvent> {
    val libraries = smartReadAction(project) {
      LibrariesUtil.findAllJarsWithClass(project, LibrariesUtil.SOME_GROOVY_CLASS)
    }

    return libraries.asSequence()
      .mapNotNull { it.parent?.path }
      .map { GroovyConfigUtils.getInstance().getSDKVersionOrNull(it) } // disk IO
      .map { VERSION_EVENT.metric(it) }
      .toSet()
  }
}