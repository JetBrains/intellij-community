// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

enum class RunnerType {
  IJCSampling, IJCTracing, IJCTracingTestTracking, JaCoCo, Emma
}

class CoverageLogger : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("coverage", 1)

    private val RUNNER_NAME = EventFields.String("runner", listOf("emma", "jacoco", "idea"))

    private val START = GROUP.registerEvent("started", EventFields.Enum("runner", RunnerType::class.java),
                                            EventFields.Int("includes"), EventFields.Int("excludes"))
    private val REPORT_LOADING = GROUP.registerEvent("report.loaded", RUNNER_NAME, EventFields.DurationMs)
    private val HTML = GROUP.registerEvent("html.generated", EventFields.DurationMs, EventFields.Long("generation_ms"))

    @JvmStatic
    fun logStarted(coverageRunner: CoverageRunner,
                   isSampling: Boolean,
                   isTrackRepTestEnabled: Boolean,
                   includePatterns: Int,
                   excludePatterns: Int) {
      val type = when (coverageRunner.id) {
        "emma" -> RunnerType.Emma
        "jacoco" -> RunnerType.JaCoCo
        "idea" -> when {
          isSampling -> RunnerType.IJCSampling
          isTrackRepTestEnabled -> RunnerType.IJCTracingTestTracking
          else -> RunnerType.IJCTracing
        }
        else -> return
      }
      START.log(type, includePatterns, excludePatterns)
    }

    @JvmStatic
    fun logReportLoading(project: Project?, coverageRunner: CoverageRunner, timeMs: Long) =
      REPORT_LOADING.log(project, coverageRunner.id, timeMs)

    @JvmStatic
    fun logHTMLReport(project: Project?, timeMs: Long, generationTimeMs: Long) = HTML.log(project, timeMs, generationTimeMs)
  }

  override fun getGroup() = GROUP
}
