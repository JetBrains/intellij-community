// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.telemetry

import com.intellij.gradle.toolingExtension.impl.telemetry.GradleTelemetryFormat
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.util.registry.Registry.Companion.stringValue
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

@Internal
object GradleDaemonOpenTelemetryUtil {

  @JvmStatic
  fun isDaemonTracingEnabled(): Boolean {
    return `is`("gradle.daemon.opentelemetry.enabled", false)
  }

  @JvmStatic
  fun getTelemetryFormat(): GradleTelemetryFormat {
    try {
      val format = stringValue("gradle.daemon.opentelemetry.format")
      return GradleTelemetryFormat.valueOf(format.uppercase())
    }
    catch (e: Exception) {
      // ignore
      return GradleTelemetryFormat.PROTOBUF
    }
  }

  @JvmStatic
  fun getTargetFolder(): Path? {
    try {
      val format = stringValue("gradle.daemon.opentelemetry.folder")
      if (format.isBlank()) {
        return null
      }
      return format.toNioPathOrNull()
    }
    catch (e: Exception) {
      // ignore
      return null
    }
  }
}
