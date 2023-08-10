// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log

import com.intellij.openapi.vcs.VcsScope
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpan.LogHistory
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpanAttribute.HISTORY_COMPUTING_VCS_NAME
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpanAttribute.IS_INITIAL_HISTORY_COMPUTING
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpanAttribute.TYPE_HISTORY_COMPUTING
import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import com.intellij.platform.diagnostic.telemetry.impl.otExporters.OpenTelemetryExporterProvider
import com.intellij.util.indexing.diagnostic.dto.toMillis
import com.intellij.vcs.log.statistics.VcsLogPerformanceStatisticsCollector.FILE_HISTORY_COLLECTING_RENAMES
import com.intellij.vcs.log.statistics.VcsLogPerformanceStatisticsCollector.FILE_HISTORY_COMPUTING
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.trace.data.SpanData

private class VcsLogTelemetryExporter : OpenTelemetryExporterProvider {
  override fun getSpanExporters(): List<AsyncSpanExporter> {
    return listOf(LogHistorySpanExporter)
  }

  private object LogHistorySpanExporter : AsyncSpanExporter {
    override suspend fun export(spans: Collection<SpanData>) {
        spans.asSequence()
          .filter { span -> span.instrumentationScopeInfo.name == VcsScope.name }
          .forEach { span ->
            LogHistory.values()
              .find { historySpan -> historySpan.name == span.name }
              ?.let { historySpan ->
                when (historySpan) {
                  LogHistory.Computing -> {
                    val vcsName = span.attributes[HISTORY_COMPUTING_VCS_NAME].orEmpty()
                    val indexComputing = "index" == span.attributes[TYPE_HISTORY_COMPUTING]
                    if (span.attributes[IS_INITIAL_HISTORY_COMPUTING] == true) {
                      FILE_HISTORY_COMPUTING.log(vcsName, indexComputing, span.valueInMillis)
                    }
                  }
                  LogHistory.CollectingRenames -> FILE_HISTORY_COLLECTING_RENAMES.log(span.valueInMillis)
                }
              }
          }
    }

    private val SpanData.valueInMillis
      get() = (endEpochNanos - startEpochNanos).toMillis()
  }

  override fun getMetricsExporters() = emptyList<MetricExporter>()
}
