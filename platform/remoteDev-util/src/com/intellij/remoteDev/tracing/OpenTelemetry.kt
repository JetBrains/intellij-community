package com.intellij.remoteDev.tracing

import com.jetbrains.rd.ide.model.RdTelemetryDataHolder
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import java.time.Instant
import java.util.concurrent.TimeUnit

const val RDCT_INSTRUMENTER_NAME = "rdct"

//this is a single instance that they recommend to use in OTel docs
val opentelemetry: OpenTelemetry by lazy {
  GlobalOpenTelemetry.get()
}
val tracer: Tracer
  get() = opentelemetry.getTracer(RDCT_INSTRUMENTER_NAME)

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
  return tracer.spanBuilder(message)
    .setSpanKind(kind)
    .setParent(parent)
    .setAttribute("start time", startTime)
    .setStartTimestamp(startTime, TimeUnit.NANOSECONDS)
    .startSpan().use(delta) {
      action(this)
    }
}

fun <T> getRemoteContext(message: T): Context where T : RdTelemetryDataHolder {
  val telemetryDataList = message.data
  val telemetryDataMap = mutableMapOf<String, String>()
  telemetryDataList?.map {
    telemetryDataMap[it.key] = it.value
  }
  val telemetryData = TelemetryData(telemetryDataMap)
  return opentelemetry.propagators.textMapPropagator.extract(Context.current(), telemetryData,
                                                             TelemetryData.otelGetter)
}

fun currentTimeWithAdjustment(delta: Long): Long {
  return getCurrentTime() - delta
}

fun getCurrentTime(): Long {
  val now = Instant.now()
  return TimeUnit.SECONDS.toNanos(now.epochSecond) + now.nano
}
