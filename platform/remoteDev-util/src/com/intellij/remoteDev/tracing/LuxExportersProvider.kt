// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.tracing

import com.intellij.diagnostic.telemetry.*
import com.intellij.diagnostic.telemetry.otExporters.OTelExportersProvider
import com.intellij.openapi.diagnostic.logger
import io.opentelemetry.sdk.metrics.export.MetricExporter
import java.io.File
import java.time.Duration

private val LOG = logger<LuxExportersProvider>()
class LuxExportersProvider : OTelExportersProvider {
  override fun getSpanExporters(): List<AsyncSpanExporter> {
    return emptyList()
  }

  override fun getMetricsExporters(): List<MetricExporter> {
    val fileToWrite: File? = try {
      CsvGzippedMetricsExporter.generatePathForLuxMetrics().toFile()
    }
    catch (e: UnsupportedOperationException) {
      LOG.warn("Failed to create a file for metrics")
      null
    }
    fileToWrite?.let {
      return listOf(
        FilteredMetricsExporter(CsvGzippedMetricsExporter(fileToWrite)) { metric ->
          metric.belongsToScope(Lux)
        })
    }
    return emptyList()
  }

  override fun getReadsInterval(): Duration {
    return Duration.ofSeconds(1)
  }

  override fun isTracingAvailable(): Boolean {
    return false
  }

  override fun areMetricsAvailable(): Boolean {
    return System.getProperty(OpenTelemetryUtils.RDCT_LUX_METRICS_DIAGNOSTIC_FLAG) != null
  }
}