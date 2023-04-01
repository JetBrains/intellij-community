// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.tracing

import com.intellij.diagnostic.telemetry.*
import com.intellij.diagnostic.telemetry.otExporters.OTelExportersProvider
import com.intellij.openapi.diagnostic.logger
import io.opentelemetry.sdk.metrics.export.MetricExporter
import java.io.File
import java.time.Duration

private val LOG = logger<RdctExportersProvider>()
class RdctExportersProvider : OTelExportersProvider {
  override fun getSpanExporters(): List<AsyncSpanExporter> {
    return listOf(MessageBusSpanExporter())
  }

  override fun getMetricsExporters(): List<MetricExporter> {
    val fileToWrite: File? = try {
      CsvGzippedMetricsExporter.generatePathForConnectionMetrics().toFile()
    }
    catch (e: UnsupportedOperationException) {
      LOG.warn("Failed to create a file for metrics")
      null
    }
    fileToWrite?.let {
      return listOf(
        FilteredMetricsExporter(CsvGzippedMetricsExporter(fileToWrite)) { metric ->
          metric.belongsToScope(RDCT)
        })
    }
    return emptyList()
  }

  override fun isTracingAvailable(): Boolean {
    return System.getProperty(OpenTelemetryUtils.RDCT_TRACING_DIAGNOSTIC_FLAG) != null &&
           System.getProperty(OpenTelemetryUtils.IDEA_DIAGNOSTIC_OTLP) != null
  }

  override fun areMetricsAvailable(): Boolean {
    return System.getProperty(OpenTelemetryUtils.RDCT_CONN_METRICS_DIAGNOSTIC_FLAG) != null
  }

  override fun getReadsInterval(): Duration {
    return Duration.ofSeconds(1)
  }
}