// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.tracing

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.diagnostic.telemetry.FilteredMetricsExporter
import com.intellij.platform.diagnostic.telemetry.OpenTelemetryUtils
import com.intellij.platform.diagnostic.telemetry.belongsToScope
import com.intellij.platform.diagnostic.telemetry.impl.CsvGzippedMetricsExporter
import com.intellij.platform.diagnostic.telemetry.impl.otExporters.OpenTelemetryExporterProvider
import com.intellij.util.concurrency.SynchronizedClearableLazy
import io.opentelemetry.sdk.metrics.export.MetricExporter
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

private class LuxExportersProvider : OpenTelemetryExporterProvider {
  override fun getMetricsExporters(): List<MetricExporter> {
    if (System.getProperty(OpenTelemetryUtils.RDCT_LUX_METRICS_DIAGNOSTIC_FLAG) == null) {
      return emptyList()
    }

    val fileToWrite: Path? = try {
      CsvGzippedMetricsExporter.generatePathForLuxMetrics()
    }
    catch (e: UnsupportedOperationException) {
      logger<LuxExportersProvider>().warn("Failed to create a file for metrics")
      null
    }

    fileToWrite?.let {
      return listOf(
        FilteredMetricsExporter(SynchronizedClearableLazy { CsvGzippedMetricsExporter(fileToWrite) }) { metric ->
          metric.belongsToScope(Lux)
        })
    }
    return emptyList()
  }

  override fun getReadInterval() = 1.seconds
}