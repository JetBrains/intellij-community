// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.dispatcher

import com.intellij.internal.statistic.eventLog.LogEventRecord
import com.intellij.internal.statistic.eventLog.LogEventRecordRequest
import com.intellij.internal.statistic.eventLog.LogEventSerializer
import com.intellij.internal.statistic.eventLog.SerializationHelper
import com.jetbrains.fus.reporting.FusJsonSerializer
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.ValidatedFusRecord
import com.jetbrains.fus.reporting.model.lion3.ValidatedFusReport
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KClass

/**
 * [FusJsonSerializer] that produces the on-disk and on-the-wire shape IntelliJ's event log has used since 2018.
 *
 * `LogEvent` (lion3) and `ValidatedFusReport` are routed through [LogEventSerializer] so the JSON keys
 * (`recorder_version`, conditional `state`/`count`, conditional `internal`, etc.) stay identical to what
 * `StatisticsEventLogFileWriter` and `EventLogStatisticsService` have produced historically. Anything else
 * (SDK configuration payloads, remote-config blobs, etc.) is delegated to [delegate] — the regular Jackson
 * serializer already used by `FusComponentProvider`.
 *
 * Without this wrapper the SDK would emit `recorderVersion` (camelCase) and always-present `count` even
 * when `state=true`, breaking the external uploader (which reads files via `LogEventDeserializer`) and
 * the metrics server (which speaks the legacy shape).
 */
@ApiStatus.Internal
class IntellijFusJsonSerializer(private val delegate: FusJsonSerializer) : FusJsonSerializer {
  override fun toJson(data: Any, prettyPrint: Boolean): String = when (data) {
    is LogEvent -> LogEventSerializer.toString(data)
    is ValidatedFusReport -> LogEventSerializer.toString(data.toLogEventRecordRequest())
    else -> delegate.toJson(data, prettyPrint)
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> fromJson(json: String, clazz: KClass<T>): T = when (clazz) {
    LogEvent::class -> SerializationHelper.deserializeLogEvent(json) as T
    ValidatedFusReport::class -> SerializationHelper.deserializeLogEventRecordRequest(json).toValidatedFusReport() as T
    else -> delegate.fromJson(json, clazz)
  }

  private fun ValidatedFusReport.toLogEventRecordRequest(): LogEventRecordRequest =
    LogEventRecordRequest(
      recorder = recorder,
      product = product,
      device = device,
      records = records.map { LogEventRecord(it.events) },
      internal = internal == true,
    )

  private fun LogEventRecordRequest.toValidatedFusReport(): ValidatedFusReport =
    ValidatedFusReport(
      product = product,
      device = device,
      recorder = recorder,
      internal = if (internal) true else null,
      records = records.map { ValidatedFusRecord(it.events) },
    )
}
