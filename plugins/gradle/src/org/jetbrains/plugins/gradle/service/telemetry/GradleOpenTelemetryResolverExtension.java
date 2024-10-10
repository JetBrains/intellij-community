// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.telemetry;

import com.intellij.platform.diagnostic.telemetry.rt.context.TelemetryContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;

import java.util.Set;

public class GradleOpenTelemetryResolverExtension extends AbstractProjectResolverExtension {

  @Override
  public @NotNull Set<Class<?>> getToolingExtensionsClasses() {
    return Set.of(Span.class, TracerProvider.class, SdkTracerProvider.class, OpenTelemetry.class, OpenTelemetrySdk.class,
                  TraceRequestMarshaler.class, TelemetryContext.class);
  }
}
