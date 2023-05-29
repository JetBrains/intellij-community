package com.intellij.remoteDev.tracing

import com.intellij.platform.diagnostic.telemetry.TelemetryTracer
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import java.time.Instant
import java.util.concurrent.TimeUnit

const val RDCT_INSTRUMENTER_NAME = "rdct"
suspend fun <T> Span.use(delta: Long, block: suspend Span.() -> T): T {
  try {
    return block()
  }
  finally {
    val finishTime = currentTimeWithAdjustment(delta)
    setAttribute("finish time", finishTime)
    setAttribute("delta", delta)
    end(finishTime, TimeUnit.NANOSECONDS)
  }
}

suspend fun <T> withSpan(message: String,
                         kind: SpanKind,
                         parent: Context = Context.current(),
                         delta: Long = 0,
                         action: suspend Span.() -> T): T {
  val startTime = currentTimeWithAdjustment(delta)
  return TelemetryTracer.getInstance().getTracer(RDCT_INSTRUMENTER_NAME).spanBuilder(message)
    .setSpanKind(kind)
    .setParent(parent)
    .setAttribute("start time", startTime)
    .setStartTimestamp(startTime, TimeUnit.NANOSECONDS)
    .startSpan().use(delta) {
      action(this)
    }
}

fun currentTimeWithAdjustment(delta: Long): Long {
  return getCurrentTime() - delta
}

fun getCurrentTime(): Long {
  val now = Instant.now()
  return TimeUnit.SECONDS.toNanos(now.epochSecond) + now.nano
}