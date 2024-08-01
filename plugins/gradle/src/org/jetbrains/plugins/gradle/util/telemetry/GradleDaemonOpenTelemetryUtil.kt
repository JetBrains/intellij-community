// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.telemetry;

import com.intellij.gradle.toolingExtension.impl.telemetry.GradleTelemetryFormat;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public final class GradleDaemonOpenTelemetryUtil {

  public static boolean isDaemonTracingEnabled() {
    return Registry.is("gradle.daemon.opentelemetry.enabled", false);
  }

  public static @NotNull GradleTelemetryFormat getTelemetryFormat() {
    try {
      String format = Registry.stringValue("gradle.daemon.opentelemetry.format");
      return GradleTelemetryFormat.valueOf(format.toUpperCase(Locale.ROOT));
    }
    catch (Exception e) {
      // ignore
      return GradleTelemetryFormat.PROTOBUF;
    }
  }
}
