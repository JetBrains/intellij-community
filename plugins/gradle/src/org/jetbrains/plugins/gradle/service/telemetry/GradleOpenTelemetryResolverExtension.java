// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.telemetry;

import com.intellij.platform.diagnostic.telemetry.rt.PlatformTelemetryRtClass;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;

import java.util.Set;

/**
 * This resolver extensions provides a set of additional telemetry-related classes to be passed onto the Gradle daemon side.
 * The Gradle's PathInference mechanism is capable of resolution of those classes, so this Resolver extension wasn't actually needed,
 * it just provides the required set of classes explicitly.
 */
public class GradleOpenTelemetryResolverExtension extends AbstractProjectResolverExtension {

  @Override
  public @NotNull Set<Class<?>> getToolingExtensionsClasses() {
    return Set.of(OpenTelemetry.class, OpenTelemetrySdk.class, PlatformTelemetryRtClass.class);
  }
}
