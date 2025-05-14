// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.statistics

import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.telemetry.VcsBackendTelemetrySpan.LogFilter
import com.intellij.openapi.vcs.telemetry.VcsBackendTelemetrySpan.LogHistory
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpanAttribute.FILE_HISTORY_IS_INITIAL
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpanAttribute.FILE_HISTORY_TYPE
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpanAttribute.VCS_LIST
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpanAttribute.VCS_LOG_FILTERED_COMMIT_COUNT
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpanAttribute.VCS_LOG_FILTERS_LIST
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpanAttribute.VCS_LOG_FILTER_KIND
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpanAttribute.VCS_LOG_GRAPH_OPTIONS_TYPE
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpanAttribute.VCS_LOG_REPOSITORY_COMMIT_COUNT
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpanAttribute.VCS_LOG_SORT_TYPE
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpanAttribute.VCS_NAME
import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import com.intellij.platform.diagnostic.telemetry.impl.OpenTelemetryExporterProvider
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.util.indexing.diagnostic.dto.toMillis
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.statistics.VcsLogPerformanceStatisticsCollector.FILE_HISTORY_COLLECTING_RENAMES
import com.intellij.vcs.log.statistics.VcsLogPerformanceStatisticsCollector.FILE_HISTORY_COMPUTING
import com.intellij.vcs.log.statistics.VcsLogPerformanceStatisticsCollector.FILTERED_COMMIT_COUNT_FIELD
import com.intellij.vcs.log.statistics.VcsLogPerformanceStatisticsCollector.FILTERS_FIELD
import com.intellij.vcs.log.statistics.VcsLogPerformanceStatisticsCollector.FILTER_KIND_FIELD
import com.intellij.vcs.log.statistics.VcsLogPerformanceStatisticsCollector.GRAPH_OPTIONS_TYPE_FIELD
import com.intellij.vcs.log.statistics.VcsLogPerformanceStatisticsCollector.REPOSITORY_COMMIT_COUNT_FIELD
import com.intellij.vcs.log.statistics.VcsLogPerformanceStatisticsCollector.SORT_TYPE_FIELD
import com.intellij.vcs.log.statistics.VcsLogPerformanceStatisticsCollector.VCS_LIST_FIELD
import com.intellij.vcs.log.statistics.VcsLogPerformanceStatisticsCollector.VCS_LOG_FILTER
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.trace.data.ExceptionEventData
import io.opentelemetry.sdk.trace.data.SpanData

private class VcsLogTelemetryExporter : OpenTelemetryExporterProvider {
  override fun getSpanExporters(): List<AsyncSpanExporter> {
    return listOf(LogHistorySpanExporter, LogFilterSpanExporter)
  }

  override fun getMetricsExporters() = emptyList<MetricExporter>()
}

private object LogHistorySpanExporter : AsyncSpanExporter {
  override suspend fun export(spans: Collection<SpanData>) {
    for (span in spans.vcsSpans()) {
      if (span.hasErrors()) continue
      val historySpan = LogHistory.entries.find { it.getName() == span.name } ?: continue

      val vcsName = span.attributes[VCS_NAME].orEmpty()
      when (historySpan) {
        LogHistory.Computing -> {
          val indexComputing = "index" == span.attributes[FILE_HISTORY_TYPE]
          if (span.attributes[FILE_HISTORY_IS_INITIAL] == true) {
            FILE_HISTORY_COMPUTING.log(vcsName, indexComputing, span.valueInMillis)
          }
        }
        LogHistory.CollectingRenames -> FILE_HISTORY_COLLECTING_RENAMES.log(vcsName, span.valueInMillis)
        else -> {}
      }
    }
  }
}

private object LogFilterSpanExporter : AsyncSpanExporter {
  override suspend fun export(spans: Collection<SpanData>) {
    for (span in spans.vcsSpans()) {
      if (span.hasErrors() || span.name != LogFilter.getName()) continue

      val filtersList = span.attributes[VCS_LOG_FILTERS_LIST]?.toStringList()
      if (filtersList.isNullOrEmpty()) continue

      val vcsList = span.attributes[VCS_LIST]?.toStringList() ?: continue
      val graphOptionsType = span.attributes[VCS_LOG_GRAPH_OPTIONS_TYPE] ?: continue
      val sortType = span.attributes[VCS_LOG_SORT_TYPE]

      val commitCount = span.attributes[VCS_LOG_FILTERED_COMMIT_COUNT] ?: continue
      val repositoryCommitCount = span.attributes[VCS_LOG_REPOSITORY_COMMIT_COUNT]
      val filterKind = span.attributes[VCS_LOG_FILTER_KIND] ?: continue

      val events = mutableListOf(VCS_LIST_FIELD.with(vcsList), FILTERS_FIELD.with(filtersList), GRAPH_OPTIONS_TYPE_FIELD.with(graphOptionsType),
                                 FILTERED_COMMIT_COUNT_FIELD.with(commitCount), REPOSITORY_COMMIT_COUNT_FIELD.with(repositoryCommitCount),
                                 FILTER_KIND_FIELD.with(filterKind), EventFields.DurationMs.with(span.valueInMillis))
      if (sortType != null) {
        events.add(SORT_TYPE_FIELD.with(sortType))
      }

      VCS_LOG_FILTER.log(events)
    }
  }
}

private fun Collection<SpanData>.vcsSpans() = asSequence().filter { span -> span.instrumentationScopeInfo.name == VcsScope.name }

private val SpanData.valueInMillis
  get() = (endEpochNanos - startEpochNanos).toMillis()

private fun SpanData.hasErrors(): Boolean {
  return status.statusCode == StatusCode.ERROR || events.any { it is ExceptionEventData }
}

private const val SEPARATOR = "\u0001"

internal fun Collection<VcsLogFilterCollection.FilterKey<*>>.filtersToStringPresentation(): String {
  return toStringPresentation(VcsLogFilterCollection.FilterKey<*>::getName)
}

internal fun Collection<VcsKey>.vcsToStringPresentation(): String {
  return toStringPresentation(VcsLogRepoSizeCollector::getVcsKeySafe)
}

private fun <T> Collection<T>.toStringPresentation(transform: (T) -> String): String {
  return this.map(transform).sorted().joinToString(SEPARATOR)
}

private fun String.toStringList(): List<String> {
  if (isEmpty()) return emptyList()
  return this.split(SEPARATOR)
}
