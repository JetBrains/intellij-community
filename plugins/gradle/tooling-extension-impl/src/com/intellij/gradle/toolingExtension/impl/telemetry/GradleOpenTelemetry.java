// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.telemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

public final class GradleOpenTelemetry {

  private static final String INSTRUMENTATION_NAME = "GradleDaemon";

  private @NotNull OpenTelemetry myOpenTelemetry = OpenTelemetry.noop();
  private @Nullable Scope myScope = null;
  private @Nullable FilteringSpanDataCollector mySpanDataCollector = null;
  private @NotNull GradleTelemetryFormat myFormat = GradleTelemetryFormat.PROTOBUF;

  public void start(@NotNull GradleTracingContext context) {
    mySpanDataCollector = new FilteringSpanDataCollector();
    myOpenTelemetry = OpenTelemetrySdk.builder()
      .setTracerProvider(SdkTracerProvider.builder()
                           .addSpanProcessor(BatchSpanProcessor.builder(mySpanDataCollector)
                                               .setMaxExportBatchSize(128)
                                               .build())
                           .setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), INSTRUMENTATION_NAME)))
                           .build())
      .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
      .build();

    myScope = injectTracingContext(myOpenTelemetry, context);
    myFormat = getRequestedTelemetryFormat(context);
  }

  public @NotNull OpenTelemetry getTelemetry() {
    return myOpenTelemetry;
  }

  public @NotNull Tracer getTracer() {
    return getTelemetry().getTracer(INSTRUMENTATION_NAME);
  }

  public <T> T callWithSpan(@NotNull String spanName, @NotNull Function<Span, T> fn) {
    return callWithSpan(spanName, (ignore) -> {
    }, fn);
  }

  public <T> T callWithSpan(@NotNull String spanName,
                            @NotNull Consumer<SpanBuilder> configurator,
                            @NotNull Function<Span, T> fn) {
    SpanBuilder spanBuilder = getTracer()
      .spanBuilder(spanName);
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

  public void runWithSpan(@NotNull String spanName, @NotNull Consumer<Span> consumer) {
    callWithSpan(spanName, span -> {
      consumer.accept(span);
      return null;
    });
  }

  public @NotNull TelemetryHolder shutdown() {
    try {
      if (myScope != null) {
        myScope.close();
      }
      if (myOpenTelemetry instanceof Closeable) {
        ((Closeable)myOpenTelemetry).close();
      }
      // the data should be exported only after OpenTelemetry was closed to prevent data loss
      if (mySpanDataCollector != null) {
        Collection<SpanData> collectedSpans = mySpanDataCollector.getCollectedSpans();
        mySpanDataCollector.clear();
        byte[] serialized = SpanDataSerializer.serialize(collectedSpans, myFormat);
        return new TelemetryHolder(myFormat, serialized);
      }
    }
    catch (Exception e) {
      // ignore
    }
    return TelemetryHolder.empty();
  }

  private static @NotNull Scope injectTracingContext(@NotNull OpenTelemetry telemetry, @NotNull GradleTracingContext context) {
    return telemetry
      .getPropagators()
      .getTextMapPropagator()
      .extract(Context.current(), context, GradleTracingContext.GETTER)
      .makeCurrent();
  }

  private static @NotNull GradleTelemetryFormat getRequestedTelemetryFormat(@NotNull GradleTracingContext context) {
    String requestedFormat = context.get(GradleTracingContext.REQUESTED_FORMAT_KEY);
    if (requestedFormat == null) {
      return GradleTelemetryFormat.PROTOBUF;
    }
    try {
      return GradleTelemetryFormat.valueOf(requestedFormat);
    }
    catch (Exception e) {
      // ignore
      return GradleTelemetryFormat.PROTOBUF;
    }
  }
}
