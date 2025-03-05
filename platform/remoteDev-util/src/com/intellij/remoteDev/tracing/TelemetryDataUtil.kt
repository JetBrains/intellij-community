package com.intellij.remoteDev.tracing

import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import java.time.Instant
import java.util.concurrent.TimeUnit

private val RDCT_INSTRUMENTER = Scope("rdct")

inline fun <T> Span.use(delta: Long, block: Span.() -> T): T {
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

inline fun <T> withSpan(message: String,
                         kind: SpanKind,
                         parent: Context = Context.current(),
                         delta: Long = 0,
                         action: Span.() -> T): T {
  val startTime = currentTimeWithAdjustment(delta)
  return rdctTracer().spanBuilder(message)
    .setSpanKind(kind)
    .setParent(parent)
    .setAttribute("start time", startTime)
    .setStartTimestamp(startTime, TimeUnit.NANOSECONDS)
    .startSpan().use(delta) {
      action(this)
    }
}

fun rdctTracer(): IJTracer = TelemetryManager.getTracer(RDCT_INSTRUMENTER)

fun currentTimeWithAdjustment(delta: Long): Long {
  return getCurrentTime() - delta
}

fun getCurrentTime(): Long {
  val now = Instant.now()
  return TimeUnit.SECONDS.toNanos(now.epochSecond) + now.nano
}