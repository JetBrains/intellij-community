// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.tracing

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import com.intellij.platform.diagnostic.telemetry.MetricsExporterEntry
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.impl.otExporters.OTelExportersProvider
import kotlinx.coroutines.CoroutineScope

private val EP: ExtensionPointName<OTelExportersProvider> = ExtensionPointName("com.intellij.oTelExportersProvider")

private class CustomExportersListener : ApplicationInitializedListener {
  override suspend fun execute(asyncScope: CoroutineScope) {
    val providers = EP.extensionList
    if (providers.isEmpty()) {
      return
    }

    val spanExporters = mutableListOf<AsyncSpanExporter>()
    val metricsExporters = mutableListOf<MetricsExporterEntry>()

    for (provider in providers) {
      if (provider.isTracingAvailable()) {
        spanExporters.addAll(provider.getSpanExporters())
      }
      if (provider.areMetricsAvailable()) {
        val metrics = provider.getMetricsExporters()
        val duration = provider.getReadInterval()
        metricsExporters.add(MetricsExporterEntry(metrics, duration))
      }
    }

    val telemetryManager = TelemetryManager.getInstance()
    telemetryManager.addSpansExporters(spanExporters)
    telemetryManager.addMetricsExporters(metricsExporters)
  }
}