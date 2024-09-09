// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.telemetry

import com.intellij.gradle.toolingExtension.impl.telemetry.GradleTelemetryFormat
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.util.registry.Registry.Companion.stringValue
import com.intellij.platform.diagnostic.telemetry.impl.getOtlpEndPoint
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import java.net.URI
import java.nio.file.Path
import java.util.function.Supplier

@Internal
object GradleDaemonOpenTelemetryUtil {

  @JvmField
  val DAEMON_TELEMETRY_ENABLED_KEY: Key<Boolean> = Key.create("DAEMON_TELEMETRY_ENABLED")

  @JvmField
  val DAEMON_TELEMETRY_FORMAT_KEY: Key<GradleTelemetryFormat> = Key.create("DAEMON_TELEMETRY_FORMAT")

  @JvmField
  val DAEMON_TELEMETRY_TARGET_FOLDER_KEY: Key<Path?> = Key.create("DAEMON_TELEMETRY_TARGET_FOLDER")

  @JvmField
  val DAEMON_TELEMETRY_TARGET_ENDPOINT_KEY: Key<URI?> = Key.create("DAEMON_TELEMETRY_TARGET_ENDPOINT")

  @JvmStatic
  fun isDaemonTracingEnabled(settings: GradleExecutionSettings): Boolean {
    return settings.getUserData(DAEMON_TELEMETRY_ENABLED_KEY)
           ?: `is`("gradle.daemon.opentelemetry.enabled", false)
  }

  @JvmStatic
  fun getTelemetryFormat(settings: GradleExecutionSettings): GradleTelemetryFormat {
    return settings.providedOrElse(DAEMON_TELEMETRY_FORMAT_KEY) {
      val format = stringValue("gradle.daemon.opentelemetry.format")
      GradleTelemetryFormat.valueOf(format.uppercase())
    } ?: GradleTelemetryFormat.PROTOBUF
  }

  @JvmStatic
  fun getTargetFolder(settings: GradleExecutionSettings): Path? {
    return settings.providedOrElse(DAEMON_TELEMETRY_TARGET_FOLDER_KEY) {
      val folder = stringValue("gradle.daemon.opentelemetry.folder").nullize()
      folder?.toNioPathOrNull()
    }
  }

  @JvmStatic
  fun getTargetEndpoint(settings: GradleExecutionSettings): URI? {
    return settings.providedOrElse(DAEMON_TELEMETRY_TARGET_ENDPOINT_KEY) {
      val uri = getOtlpEndPoint() ?: return@providedOrElse null
      URI.create(uri)
    }
  }

  private fun <T> GradleExecutionSettings.providedOrElse(key: Key<T>, fn: Supplier<T?>): T? {
    val provided = getUserData(key)
    if (provided != null) {
      return provided
    }
    try {
      return fn.get()
    }
    catch (_: Exception) {
      // ignore
      return null
    }
  }
}
