// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.tooling.telemetry;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FilteringSpanDataCollector implements SpanExporter {

  private static final long SPAN_DURATION_THRESHOLD_NANOS = TimeUnit.MILLISECONDS.toNanos(15);

  private final @NotNull List<SpanData> collectedSpans = new ArrayList<>();

  @Override
  public @NotNull CompletableResultCode export(@NotNull Collection<SpanData> spans) {
    for (SpanData span : spans) {
      if (span.getEndEpochNanos() - span.getStartEpochNanos() >= SPAN_DURATION_THRESHOLD_NANOS) {
        collectedSpans.add(span);
      }
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

  public @NotNull List<SpanData> getCollectedSpans() {
    return collectedSpans;
  }
}
