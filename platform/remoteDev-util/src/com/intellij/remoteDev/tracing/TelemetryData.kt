package com.intellij.remoteDev.tracing

import com.intellij.remoteDev.tracing.TelemetryData.Companion.otelSetter
import com.jetbrains.rd.ide.model.RdTelemetryData
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapSetter

data class TelemetryData(val traceContext: Map<String, String>) {
  companion object {
    val otelGetter: TextMapGetter<TelemetryData> = object : TextMapGetter<TelemetryData> {
      override fun keys(carrier: TelemetryData): Iterable<String> = carrier.traceContext.keys

      override fun get(carrier: TelemetryData?, key: String): String? = carrier?.traceContext?.get(key)
    }

    val otelSetter: TextMapSetter<MutableMap<String, String>> = TextMapSetter<MutableMap<String, String>> { carrier, key, value ->
      carrier?.set(key, value)
    }
  }
}

fun Context.getTelemetryMap(): Map<String, String> {
  return mutableMapOf<String, String>().apply {
    opentelemetry.propagators.textMapPropagator.inject(this@getTelemetryMap, this, otelSetter)
  }
}

fun Map<String, String>.toModel(): List<RdTelemetryData> {
  return this.map { RdTelemetryData(it.key, it.value) }
}
