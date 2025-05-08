// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.tracing

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.diagnostic.telemetry.*
import com.intellij.platform.diagnostic.telemetry.exporters.meters.CsvGzippedMetricsExporter
import com.intellij.platform.diagnostic.telemetry.impl.MessageBusSpanExporter
import com.intellij.platform.diagnostic.telemetry.impl.OpenTelemetryExporterProvider
import com.intellij.util.concurrency.SynchronizedClearableLazy
import io.opentelemetry.sdk.metrics.export.MetricExporter
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

private class RdctExportersProvider : OpenTelemetryExporterProvider {
  override fun getSpanExporters(): List<AsyncSpanExporter> {
    if (System.getProperty(OpenTelemetryUtils.RDCT_TRACING_DIAGNOSTIC_FLAG) != null && OtlpConfiguration.isTraceEnabled()) {
      return listOf(MessageBusSpanExporter())
    }
    else {
      return emptyList()
    }
  }

  override fun getMetricsExporters(): List<MetricExporter> {
    if (System.getProperty(OpenTelemetryUtils.RDCT_CONN_METRICS_DIAGNOSTIC_FLAG) == null) {
      return emptyList()
    }

    val fileToWrite: Path? = try {
      CsvGzippedMetricsExporter.generatePathForConnectionMetrics()
    }
    catch (e: UnsupportedOperationException) {
      logger<RdctExportersProvider>().warn("Failed to create a file for metrics")
      null
    }
    fileToWrite?.let {
      return listOf(FilteredMetricsExporter(SynchronizedClearableLazy { CsvGzippedMetricsExporter(fileToWrite) }) { metric ->
        metric.belongsToScope(RDCT)
      })
    }
    return emptyList()
  }

  override fun getReadInterval() = 1.seconds
}