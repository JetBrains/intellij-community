package com.intellij.driver.sdk

import com.intellij.driver.client.Remote

@Remote(value = "com.intellij.platform.diagnostic.telemetry.TelemetryManager")
interface TelemetryManager {
  fun getInstance(): TelemetryManager

  fun forceFlushMetricsBlocking()

  fun shutdownExportersBlocking()
}