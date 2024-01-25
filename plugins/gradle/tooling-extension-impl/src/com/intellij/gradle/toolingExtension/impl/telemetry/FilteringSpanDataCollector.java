// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.telemetry;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class FilteringSpanDataCollector implements SpanExporter {

  private static final long SPAN_DURATION_THRESHOLD_NANOS = TimeUnit.MILLISECONDS.toNanos(15);

  private final @NotNull Map<String, SpanData> collectedSpans = new HashMap<>();

  @Override
  public @NotNull CompletableResultCode export(@NotNull Collection<SpanData> spans) {
    for (SpanData span : spans) {
      collectedSpans.put(span.getSpanId(), span);
    }
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public @NotNull CompletableResultCode flush() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public @NotNull CompletableResultCode shutdown() {
    return CompletableResultCode.ofSuccess();
  }

  public @NotNull Collection<SpanData> getCollectedSpans() {
    Map<String, SpanData> processedSpans = new HashMap<>();
    for (Map.Entry<String, SpanData> entry : collectedSpans.entrySet()) {
      SpanData span = entry.getValue();
      if (isValid(span)) {
        processedSpans.put(entry.getKey(), span);
        String parentSpanId = span.getParentSpanId();
        addParent(processedSpans, collectedSpans, parentSpanId);
      }
    }
    collectedSpans.clear();
    return processedSpans.values();
  }

  private static void addParent(@NotNull Map<String, SpanData> target, @NotNull Map<String, SpanData> source, @NotNull String spanId) {
    if (!target.containsKey(spanId)) {
      SpanData span = source.get(spanId);
      if (span != null) {
        target.put(spanId, span);
        String parentSpanId = span.getParentSpanId();
        if (parentSpanId != null) {
          addParent(target, source, parentSpanId);
        }
      }
    }
  }

  private static boolean isValid(@NotNull SpanData span) {
    return span.getEndEpochNanos() - span.getStartEpochNanos() >= SPAN_DURATION_THRESHOLD_NANOS;
  }
}
