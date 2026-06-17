// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.dispatcher

import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.jetbrains.fus.reporting.FusClientConfig
import com.jetbrains.fus.reporting.FusHttpClient
import com.jetbrains.fus.reporting.FusJsonSerializer
import com.jetbrains.fus.reporting.FusLoggerFactory
import com.jetbrains.fus.reporting.FusReportDispatcher
import com.jetbrains.fus.reporting.MessageBus
import com.jetbrains.fus.reporting.RemoteConfig
import com.jetbrains.fus.reporting.ReportValidator
import com.jetbrains.fus.reporting.defaults.dispatcher.AbstractSimpleReportDispatcher
import com.jetbrains.fus.reporting.defaults.dispatcher.EventQueue
import com.jetbrains.fus.reporting.defaults.dispatcher.SimpleLegacyReportDispatcher
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.ValidatedFusReport
import org.jetbrains.annotations.ApiStatus
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * IntelliJ-flavoured [FusReportDispatcher] backed by a [SimpleLegacyReportDispatcher].
 *
 * Generics match the underlying [SimpleLegacyReportDispatcher] (`<LogEvent, ValidatedFusReport>`), so events and the
 * built report flow through without conversion. The wrapper exists to gate [queueEvent] and [send] on the consent flags
 * from the IntelliJ event log provider (which `SimpleLegacyReportDispatcher` is unaware of), and to trigger
 * [ExternalUploadOrchestrator] from the `postClose` hook so the external uploader JVM still starts exactly as the
 * legacy `EventLogApplicationLifecycleListener` used to do.
 *
 * The instance is owned by `FusComponents` (see `FusComponentProvider`).
 */
@ApiStatus.Internal
class IntellijReportDispatcher(
  private val eventLogProvider: StatisticsEventLoggerProvider,
  messageBus: MessageBus,
  config: FusClientConfig,
  remoteConfig: RemoteConfig,
  jsonSerializer: FusJsonSerializer,
  httpClient: FusHttpClient,
  fusLoggerFactory: FusLoggerFactory,
  validator: ReportValidator<LogEvent>,
  eventQueue: EventQueue<LogEvent>,
  device: String,
  isInternal: Boolean,
) : FusReportDispatcher<LogEvent, ValidatedFusReport> {
  private val delegate: SimpleLegacyReportDispatcher = SimpleLegacyReportDispatcher(
    messageBus = messageBus,
    config = config,
    remoteConfig = remoteConfig,
    jsonSerializer = jsonSerializer,
    httpClient = httpClient,
    fusLoggerFactory = fusLoggerFactory,
    validator = validator,
    eventQueue = eventQueue,
    device = device,
    internal = isInternal,
    systemLogGroupId = "${eventLogProvider.recorderId.lowercase(Locale.ENGLISH)}.event.log",
    initialDelay = eventLogProvider.sendFrequencyMs.milliseconds,
    eventBufferSize = DEFAULT_EVENT_BUFFER_SIZE,
    escapeCharsInData = eventLogProvider.isCharsEscapingRequired,
    extensionsProvider = {
      postClose = postCloseHook
    },
  )

  /** Enqueue an event for later upload. Drops the event when recording or collection is disabled, mirroring [StatisticsFileEventLogger]. */
  override suspend fun queueEvent(event: LogEvent) {
    if (!eventLogProvider.isRecordEnabled()) return
    if (!StatisticsUploadAssistant.isCollectAllowed()) return
    delegate.queueEvent(event)
  }

  /** Flush queued events to the backend. No-op when sending is disabled. */
  override suspend fun send(): Boolean {
    if (!eventLogProvider.isSendEnabled()) return false
    return delegate.send()
  }

  /** Start the periodic send loop. Inner loop calls [send], which is itself gated. */
  override suspend fun scheduleSend() {
    delegate.scheduleSend()
  }

  /** Flush in-memory buffer to disk without sending. */
  override suspend fun flush() {
    delegate.flush()
  }

  /** Close the dispatcher. Triggers the registered `postClose` hook (starts the external uploader JVM). */
  override suspend fun close() {
    delegate.close()
  }

  companion object {
    private const val DEFAULT_EVENT_BUFFER_SIZE: Int = 5000

    private val postCloseHook: suspend (AbstractSimpleReportDispatcher<*, *>) -> Unit = { _ ->
      ExternalUploadOrchestrator.tryStartExternalUpload()
    }
  }
}
