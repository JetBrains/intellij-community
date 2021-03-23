// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

enum class RunnerType {
  IJCSampling, IJCTracing, IJCTracingTestTracking, JaCoCo, Emma
}

class CoverageLogger : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("coverage.idea", 1)

    private val RUNNER_NAME = EventFields.String("runner", listOf("emma", "jacoco", "idea"))

    private val RUNNER = GROUP.registerEvent("runner", EventFields.Enum("runner", RunnerType::class.java))
    private val PATTERNS = GROUP.registerEvent("patterns", EventFields.Int("includes"), EventFields.Int("excludes"))
    private val REPORT_LOADING = GROUP.registerEvent("report_loading", RUNNER_NAME, EventFields.DurationMs)
    private val HTML = GROUP.registerEvent("html", EventFields.DurationMs, EventFields.Long("generation_ms"))

    @JvmStatic
    fun logRunner(coverageRunner: CoverageRunner, isSampling: Boolean, isTrackRepTestEnabled: Boolean) {
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
      RUNNER.log(type)
    }

    @JvmStatic
    fun logPatterns(includePatterns: Int, excludePatterns: Int) = PATTERNS.log(includePatterns, excludePatterns)

    @JvmStatic
    fun logReportLoading(coverageRunner: CoverageRunner, timeMs: Long) = REPORT_LOADING.log(coverageRunner.id, timeMs)

    @JvmStatic
    fun logHTMLReport(timeMs: Long, generationTimeMs: Long) = HTML.log(timeMs, generationTimeMs)
  }

  override fun getGroup() = GROUP
}
