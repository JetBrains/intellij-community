// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.telemetry;

import com.intellij.platform.diagnostic.telemetry.rt.context.TelemetryContext;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

public final class GradleOpenTelemetry {

  private static final @NotNull String INSTRUMENTATION_NAME = "GradleDaemon";
  private final @NotNull Tracer myTracer;
  private final @NotNull Scope myScope;

  public GradleOpenTelemetry(@Nullable TelemetryContext tracingContext) {
    OpenTelemetry telemetry = GlobalOpenTelemetry.get();
    myTracer = telemetry.getTracer(INSTRUMENTATION_NAME);
    myScope = extractScope(telemetry, tracingContext);
  }

  public <T> T callWithSpan(@NotNull String spanName, @NotNull Function<Span, T> fn) {
    return callWithSpan(spanName, (ignore) -> {
    }, fn);
  }

  public void runWithSpan(@NotNull String spanName, @NotNull Consumer<Span> consumer) {
    callWithSpan(spanName, span -> {
      consumer.accept(span);
      return null;
    });
  }

  public void shutdown() {
    myScope.close();
  }

  private <T> T callWithSpan(
    @NotNull String spanName,
    @NotNull Consumer<SpanBuilder> configurator,
    @NotNull Function<Span, T> fn
  ) {
    SpanBuilder spanBuilder = myTracer.spanBuilder(spanName);
    configurator.accept(spanBuilder);
    Span span = spanBuilder.startSpan();
    try (Scope ignore = span.makeCurrent()) {
      return fn.apply(span);
    }
    catch (Exception e) {
      span.recordException(e);
      span.setStatus(StatusCode.ERROR);
      throw e;
    }
    finally {
      span.end();
    }
  }

  private static @NotNull Scope extractScope(@NotNull OpenTelemetry telemetry, @Nullable TelemetryContext context) {
    if (context == null) {
      return Scope.noop();
    }
    TextMapPropagator propagator = telemetry.getPropagators()
      .getTextMapPropagator();
    return context.extract(propagator)
      .makeCurrent();
  }
}
