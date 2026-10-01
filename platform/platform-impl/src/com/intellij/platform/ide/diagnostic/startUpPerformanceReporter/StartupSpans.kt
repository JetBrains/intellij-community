// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.diagnostic.startUpPerformanceReporter

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import io.opentelemetry.api.trace.SpanBuilder
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.TimeUnit

/**
 * Records the span [name] from [startNanos] to [endNanos] in the start-up trace. Both times are [System.nanoTime] values.
 * Use [configure] to add attributes to the span.
 */
@ApiStatus.Internal
fun recordStartupSpan(name: String, startNanos: Long, endNanos: Long, configure: (SpanBuilder) -> Unit = {}) {
  val unixNanoDiff = StartUpMeasurer.getStartTimeUnixNanoDiff()
  val spanBuilder = TelemetryManager.getTracer(Scope("startup")).spanBuilder(name)
    .setStartTimestamp(startNanos + unixNanoDiff, TimeUnit.NANOSECONDS)
  configure(spanBuilder)
  spanBuilder.startSpan().end(endNanos + unixNanoDiff, TimeUnit.NANOSECONDS)
}
