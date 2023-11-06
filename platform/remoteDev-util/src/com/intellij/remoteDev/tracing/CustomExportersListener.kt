// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.tracing

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.diagnostic.telemetry.MetricsExporterEntry
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.impl.OpenTelemetryExporterProvider
import kotlinx.coroutines.CoroutineScope

private val EP: ExtensionPointName<OpenTelemetryExporterProvider> = ExtensionPointName("com.intellij.openTelemetryExporterProvider")

private class CustomExportersListener : ApplicationInitializedListener {
  override suspend fun execute(asyncScope: CoroutineScope) {
    val providers = EP.extensionList
    if (providers.isEmpty()) {
      return
    }

    val metricsExporters = mutableListOf<MetricsExporterEntry>()
    for (provider in providers) {
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