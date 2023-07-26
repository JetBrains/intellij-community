// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.tracing

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import com.intellij.platform.diagnostic.telemetry.FilteredMetricsExporter
import com.intellij.platform.diagnostic.telemetry.OpenTelemetryUtils
import com.intellij.platform.diagnostic.telemetry.belongsToScope
import com.intellij.platform.diagnostic.telemetry.impl.CsvGzippedMetricsExporter
import com.intellij.platform.diagnostic.telemetry.impl.MessageBusSpanExporter
import com.intellij.platform.diagnostic.telemetry.impl.getOtlpEndPoint
import com.intellij.platform.diagnostic.telemetry.impl.otExporters.OTelExportersProvider
import com.intellij.util.concurrency.SynchronizedClearableLazy
import io.opentelemetry.sdk.metrics.export.MetricExporter
import java.io.File
import java.time.Duration

private class RdctExportersProvider : OTelExportersProvider {
  override fun getSpanExporters(): List<AsyncSpanExporter> {
    return listOf(MessageBusSpanExporter())
  }

  override fun getMetricsExporters(): List<MetricExporter> {
    val fileToWrite: File? = try {
      CsvGzippedMetricsExporter.generatePathForConnectionMetrics().toFile()
    }
    catch (e: UnsupportedOperationException) {
      logger<RdctExportersProvider>().warn("Failed to create a file for metrics")
      null
    }
    fileToWrite?.let {
      return listOf(
        FilteredMetricsExporter(SynchronizedClearableLazy { CsvGzippedMetricsExporter(fileToWrite) }) { metric ->
          metric.belongsToScope(RDCT)
        })
    }
    return emptyList()
  }

  override fun isTracingAvailable(): Boolean {
    return System.getProperty(OpenTelemetryUtils.RDCT_TRACING_DIAGNOSTIC_FLAG) != null && getOtlpEndPoint() != null
  }

  override fun areMetricsAvailable(): Boolean {
    return System.getProperty(OpenTelemetryUtils.RDCT_CONN_METRICS_DIAGNOSTIC_FLAG) != null
  }

  override fun getReadInterval(): Duration {
    return Duration.ofSeconds(1)
  }
}