// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.telemetry

import com.intellij.gradle.toolingExtension.impl.telemetry.GradleTelemetryFormat
import com.intellij.gradle.toolingExtension.impl.telemetry.TelemetryHolder
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.diagnostic.telemetry.exporters.OpenTelemetryRawTraceExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

@Service(Service.Level.APP)
class GradleOpenTelemetryTraceService(private val coroutineScope: CoroutineScope) {

  private fun exportTraces(holder: TelemetryHolder, path: Path?, url: URI?) {
    if (holder.traces.isEmpty()) {
      return
    }
    url?.apply { sendTelemetryToOtlp(holder, this) }
    path?.apply { dumpTelemetryToFile(holder, this) }
  }

  private fun sendTelemetryToOtlp(holder: TelemetryHolder, telemetryHost: URI) {
    coroutineScope.launch(Dispatchers.IO) {
      when (holder.format) {
        GradleTelemetryFormat.PROTOBUF -> OpenTelemetryRawTraceExporter.sendProtobuf(telemetryHost, holder.traces)
        GradleTelemetryFormat.JSON -> OpenTelemetryRawTraceExporter.sendJson(telemetryHost, holder.traces)
      }
    }
  }

  private fun dumpTelemetryToFile(holder: TelemetryHolder, targetFolder: Path) {
    coroutineScope.launch(Dispatchers.IO) {
      try {
        if (!targetFolder.exists()) {
          targetFolder.createDirectory()
        }
        val targetFile = targetFolder.resolve("gradle-telemetry-${System.currentTimeMillis()}.json")
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
    fun exportOpenTelemetry(holder: TelemetryHolder, path: Path?, url: URI?) {
      service<GradleOpenTelemetryTraceService>().exportTraces(holder, path, url)
    }
  }
}
