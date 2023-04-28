// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.tracing

import com.intellij.diagnostic.telemetry.AsyncSpanExporter
import com.intellij.diagnostic.telemetry.MetricsExporterEntry
import com.intellij.diagnostic.telemetry.TraceManager.addMetricsExporters
import com.intellij.diagnostic.telemetry.TraceManager.addSpansExporters
import com.intellij.diagnostic.telemetry.otExporters.OTelExportersProvider
import com.intellij.ide.ApplicationInitializedListener
import kotlinx.coroutines.CoroutineScope

class CustomExportersListener: ApplicationInitializedListener {
  override suspend fun execute(asyncScope: CoroutineScope) {
    val spanExporters = mutableListOf<AsyncSpanExporter>()
    val metricsExporters = mutableListOf<MetricsExporterEntry>()

    val providers = OTelExportersProvider.EP.extensionList
    if (providers.isEmpty()) return
    for (provider in providers) {
      if (provider.isTracingAvailable()) {
        spanExporters.addAll(provider.getSpanExporters())
      }
      if (provider.areMetricsAvailable()) {
        val metrics = provider.getMetricsExporters()
        val duration = provider.getReadsInterval()
        metricsExporters.add(MetricsExporterEntry(metrics, duration))
      }
    }
    addSpansExporters(*spanExporters.toTypedArray())
    addMetricsExporters(*metricsExporters.toTypedArray())
  }
}