// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.tracing

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.diagnostic.telemetry.MetricsExporterEntry
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.impl.OpenTelemetryExporterProvider

private class CustomExportersListener : ApplicationInitializedListener {
  override suspend fun execute() {
    val ep = ExtensionPointName<OpenTelemetryExporterProvider>("com.intellij.openTelemetryExporterProvider")
    if (!ep.hasAnyExtensions()) {
      return
    }

    val metricsExporters = mutableListOf<MetricsExporterEntry>()
    for (provider in ep.lazySequence()) {
      val metrics = provider.getMetricsExporters()
      if (metrics.isNotEmpty()) {
        metricsExporters.add(MetricsExporterEntry(metrics = metrics, duration = provider.getReadInterval()))
      }
    }

    if (metricsExporters.isNotEmpty()) {
      TelemetryManager.getInstance().addMetricsExporters(metricsExporters)
    }
  }
}