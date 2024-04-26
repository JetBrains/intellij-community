// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.telemetry

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.diagnostic.telemetry.exporters.OpenTelemetryRawTraceExporter
import com.intellij.platform.diagnostic.telemetry.impl.getOtlpEndPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.net.URI

@Service(Service.Level.APP)
class GradleOpenTelemetryTraceService(private val coroutineScope: CoroutineScope) {

  private fun exportTraces(binaryTraces: ByteArray) {
    if (binaryTraces.isEmpty()) return
    val telemetryHost = getOtlpEndPoint() ?: return
    coroutineScope.launch {
      OpenTelemetryRawTraceExporter.export(URI.create(telemetryHost), binaryTraces, OpenTelemetryRawTraceExporter.Protocol.PROTOBUF)
    }
  }

  companion object {

    @JvmStatic
    fun exportOpenTelemetryTraces(binaryTraces: ByteArray) {
      service<GradleOpenTelemetryTraceService>().exportTraces(binaryTraces)
    }
  }
}
