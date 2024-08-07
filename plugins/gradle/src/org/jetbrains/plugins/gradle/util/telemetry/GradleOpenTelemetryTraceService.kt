// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.telemetry

import com.intellij.gradle.toolingExtension.impl.telemetry.GradleTelemetryFormat
import com.intellij.gradle.toolingExtension.impl.telemetry.TelemetryHolder
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.diagnostic.telemetry.exporters.OpenTelemetryRawTraceExporter
import com.intellij.platform.diagnostic.telemetry.impl.getOtlpEndPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI
import java.nio.file.StandardOpenOption
import kotlin.io.path.*

@Service(Service.Level.APP)
class GradleOpenTelemetryTraceService(private val coroutineScope: CoroutineScope) {

  private fun exportTraces(holder: TelemetryHolder) {
    if (holder.traces.isEmpty()) {
      return
    }
    sendTelemetryToOtlp(holder)
    dumpTelemetryToFile(holder)
  }

  private fun sendTelemetryToOtlp(holder: TelemetryHolder) {
    val telemetryHost = getOtlpEndPoint() ?: return
    coroutineScope.launch(Dispatchers.IO) {
      when (holder.format) {
        GradleTelemetryFormat.PROTOBUF -> OpenTelemetryRawTraceExporter.sendProtobuf(URI.create(telemetryHost), holder.traces)
        GradleTelemetryFormat.JSON -> OpenTelemetryRawTraceExporter.sendJson(URI.create(telemetryHost), holder.traces)
      }
    }
  }

  private fun dumpTelemetryToFile(holder: TelemetryHolder) {
    if (holder.format == GradleTelemetryFormat.PROTOBUF) {
      return
    }
    val targetFolder = GradleDaemonOpenTelemetryUtil.getTargetFolder() ?: return
    coroutineScope.launch(Dispatchers.IO) {
      try {
        if (!targetFolder.exists()) {
          targetFolder.createDirectory()
        }
        val targetFile = targetFolder.resolve("gradle-ot-${System.currentTimeMillis()}.json")
          .createFile()
        targetFile.writeBytes(holder.traces, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      }
      catch (e: Exception) {
        LOG.warn("Unable to dump performance traces to the folder ($targetFolder); Cause: ${e.message}")
      }
    }
  }

  companion object {
    private val LOG: Logger = logger<GradleOpenTelemetryTraceService>()

    @JvmStatic
    fun exportOpenTelemetry(holder: TelemetryHolder) {
      service<GradleOpenTelemetryTraceService>().exportTraces(holder)
    }
  }
}
