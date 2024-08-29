// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.telemetry

import com.intellij.gradle.toolingExtension.impl.telemetry.GradleTelemetryFormat
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.util.registry.Registry.Companion.stringValue
import com.intellij.platform.diagnostic.telemetry.impl.getOtlpEndPoint
import org.jetbrains.annotations.ApiStatus.Internal
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
  fun isDaemonTracingEnabled(holder: UserDataHolder): Boolean {
    return holder.providedOrElse(DAEMON_TELEMETRY_ENABLED_KEY) {
      `is`("gradle.daemon.opentelemetry.enabled", false)
    } == true
  }

  @JvmStatic
  fun getTelemetryFormat(holder: UserDataHolder): GradleTelemetryFormat {
    return holder.providedOrElse(DAEMON_TELEMETRY_FORMAT_KEY) {
      val format = stringValue("gradle.daemon.opentelemetry.format")
      GradleTelemetryFormat.valueOf(format.uppercase())
    } ?: GradleTelemetryFormat.PROTOBUF
  }

  @JvmStatic
  fun getTargetFolder(holder: UserDataHolder): Path? {
    return holder.providedOrElse(DAEMON_TELEMETRY_TARGET_FOLDER_KEY) {
      val folder = stringValue("gradle.daemon.opentelemetry.folder")
      if (folder.isBlank()) {
        return@providedOrElse null
      }
      folder.toNioPathOrNull()
    }
  }

  @JvmStatic
  fun getTargetEndpoint(holder: UserDataHolder): URI? {
    return holder.providedOrElse(DAEMON_TELEMETRY_TARGET_ENDPOINT_KEY) {
      val uri = getOtlpEndPoint() ?: return@providedOrElse null
      URI.create(uri)
    }
  }

  private fun <T> UserDataHolder.providedOrElse(key: Key<T>, fn: Supplier<T?>): T? {
    val provided = getUserData(key)
    if (provided != null) {
      return provided
    }
    try {
      return fn.get()
    }
    catch (e: Exception) {
      // ignore
      return null
    }
  }
}
