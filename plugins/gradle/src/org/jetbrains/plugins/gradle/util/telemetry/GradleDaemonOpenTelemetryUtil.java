// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.telemetry;

import com.intellij.openapi.util.registry.Registry;

public final class GradleDaemonOpenTelemetryUtil {

  public static boolean isDaemonTracingEnabled() {
    return Registry.is("gradle.daemon.opentelemetry.enabled", false);
  }
}
