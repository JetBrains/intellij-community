// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.collectors.fus.ClassNameRuleValidator
import com.intellij.internal.statistic.config.eventLog.EventLogBuildType
import com.intellij.internal.statistic.eventLog.connection.StatisticsResult
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataLoadException
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataParseException
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUpdateError
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUpdateStage
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.eventLog.events.EventId3
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.uploader.EventLogUploadException.EventLogUploadErrorType
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.uploader.EventLogExternalSendConfig
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus
import java.util.Locale

/**
 * The event log group records internal events of metadata event-log for each recorder.
 * This group isn't registered as EP.
 * We add it to the metadata scheme separately in [com.intellij.internal.statistic.eventLog.events.scheme.EventsSchemeBuilder.buildEventsScheme].
 * Each recorder has its own event log group.
 * Id = 'recorderId in lowercase'.event.log.
 * Recorder = recorderId.
 * Version = version of the event logger provider.
 */
@Suppress("StatisticsCollectorNotRegistered")
@ApiStatus.Internal
open class EventLogSystemCollector(eventLoggerProvider: StatisticsEventLoggerProvider) : CounterUsagesCollector() {
  private val id = "${eventLoggerProvider.recorderId.lowercase(Locale.ENGLISH)}.event.log"
  private val GROUP = EventLogGroup(
    id,
    // Increase the group's versions locally
    // and not increase the versions in all StatisticsEventLoggerProvider
    // in case of any changes in the groups
    eventLoggerProvider.version + 9,
    eventLoggerProvider.recorderId
  )
  override fun getGroup(): EventLogGroup = GROUP

  private val metadataLoadedEvent = GROUP.registerEvent("metadata.loaded", EventFields.Version)
  private val metadataUpdatedEvent = GROUP.registerEvent("metadata.updated", EventFields.Version)
  private val metadataLoadFailedEvent = GROUP.registerVarargEvent(
    "metadata.load.failed",
    stageMetadataLoadFailedField, errorMetadataLoadFailedField, codeMetadataLoadFailedField, retryCountField
  )
  private val metadataUpdateFailedEvent = GROUP.registerVarargEvent(
    "metadata.update.failed",
    stageMetadataUpdateFailedField, errorMetadataUpdateFailedField, codeMetadataUpdateFailedField, retryCountField
  )
  private val logsSendEvent = GROUP.registerVarargEvent(
    "logs.send",
    totalLogsSendField, sendLogsSendField, failedLogsSendField, externalLogsSendField, pathsLogsField,
    succeedLogsSendField, errorsLogsSendField
  )
  private val externalSendStartedEvent = GROUP.registerEvent("external.send.started", sendTSExternalSendStarted)
  private val externalSendFinishedEvent = GROUP.registerEvent(
    "external.send.finished",
    sendTSExternalSendFinishedField, succeedExternalSendFinishedField, errorExternalSendFinishedField
  )
  private val externalSendCommandCreationStartedEvent = GROUP.registerEvent("external.send.command.creation.started")
  private val externalSendCommandCreationFinishedEvent = GROUP.registerVarargEvent(
    "external.send.command.creation.finished",
    succeedExternalSendCommandCreationFinishedField, errorExternalSendCommandCreationFinishedField
  )
  private val loadingConfigFailedEvent = GROUP.registerVarargEvent(
    "loading.config.failed",
    errorLoadingConfigFailedField, errorTSLoadingConfigFailedField
  )
  private val sentFilesCountCalculated = GROUP.registerEvent(
    "sent.logs.files.calculated",
    totalFilesCount, maxSentFilesCount, sentFilesCount
  )
  private val deletedFilesCalculated: EventId3<Int, Int, Int> = GROUP.registerEvent("deleted.logs.files.calculated",
                                                                                    totalFilesCount, deletedFilesCount, failedDeletingFilesCount
  )

  private val fileMetricsCalculated: EventId2<Long, Int> = GROUP.registerEvent("logs.file.metrics.calculated",
                                                                               fileSizeBytes, eventsCount
  )

  private val backlogSnapshot: EventId3<Int, Long, Long> = GROUP.registerEvent("logs.backlog.snapshot",
                                                                               pendingFilesCountField, pendingTotalBytesField, oldestPendingFileWaitMsField
  )

  private val dictionaryListLoadFailed = GROUP.registerVarargEvent(
    "dictionary.list.load.failed",
    stageMetadataLoadFailedField, errorMetadataLoadFailedField, codeMetadataLoadFailedField, retryCountField
  )
  private val dictionaryListUpdateFailed = GROUP.registerVarargEvent(
    "dictionary.list.update.failed",
    stageMetadataUpdateFailedField, errorMetadataUpdateFailedField, codeMetadataUpdateFailedField, retryCountField
  )
  private val dictionaryLoadedEvent: EventId3<Long, String?, Int> = GROUP.registerEvent(
    "dictionary.loaded",
    EventFields.Long("dictionary_last_modified"), dictionaryNameField, retryCountField
  )
  private val dictionaryLoadFailedEvent = GROUP.registerVarargEvent(
    "dictionary.load.failed",
    stageMetadataLoadFailedField, errorMetadataLoadFailedField, codeMetadataLoadFailedField, retryCountField, dictionaryNameField
  )
  private val dictionaryUpdatedEvent: EventId3<Long, String?, Int> = GROUP.registerEvent(
    "dictionary.updated",
    EventFields.Long("dictionary_last_modified"), dictionaryNameField, retryCountField
  )
  private val dictionaryUpdateFailedEvent = GROUP.registerVarargEvent(
    "dictionary.update.failed",
    stageMetadataUpdateFailedField, errorMetadataUpdateFailedField, codeMetadataUpdateFailedField, retryCountField, dictionaryNameField
  )

  private val fileDeletedEvent = GROUP.registerVarargEvent(
    "logs.file.deleted",
    deletedFileCauseField, deletedFileAgeMsField, deletedFileQueuedMsField, deletedFileSizeBytesField, deletedFileBuildTypeField
  )

  fun logMetadataLoaded(version: String?) = metadataLoadedEvent.log(version)
  fun logMetadataUpdated(version: String?) = metadataUpdatedEvent.log(version)
  fun logMetadataLoadFailed(error: EventLogMetadataUpdateError, retryCount: Int) {
    metadataLoadFailedEvent.log(loadFailedData(error, retryCount))
  }

  fun logMetadataUpdateFailed(error: EventLogMetadataUpdateError, retryCount: Int) {
    metadataUpdateFailedEvent.log(updateFailedData(error, retryCount))
  }

  fun logFilesSend(total: Int, succeed: Int, failed: Int, external: Boolean, successfullySentFiles: List<String>, errors: List<Int>) {
    logsSendEvent.log(
      totalLogsSendField.with(total),
      sendLogsSendField.with(succeed + failed),
      failedLogsSendField.with(failed),
      externalLogsSendField.with(external),
      pathsLogsField.with(successfullySentFiles),
      succeedLogsSendField.with(succeed),
      errorsLogsSendField.with(errors)
    )
  }

  fun logStartingExternalSend(time: Long) {
    externalSendStartedEvent.log(time)
  }

  fun logExternalSendFinished(error: String?, time: Long) {
    val succeed = StringUtil.isEmpty(error)
    externalSendFinishedEvent.log(time, succeed, error)
  }

  fun logExternalSendCommandCreationStarted() {
    externalSendCommandCreationStartedEvent.log()
  }

  fun logExternalSendCommandCreationFinished(errorType: EventLogUploadErrorType?) {
    val eventPairs = mutableListOf<EventPair<*>>(succeedExternalSendCommandCreationFinishedField.with(errorType == null))
    if (errorType != null) {
      eventPairs.add(errorExternalSendCommandCreationFinishedField.with(errorType))
    }
    externalSendCommandCreationFinishedEvent.log(eventPairs)
  }

  fun logLoadingConfigFailed(errorClass: String, time: Long) {
    val eventPairs = mutableListOf<EventPair<*>>(errorLoadingConfigFailedField.with(errorClass))
    if (time != -1L) {
      eventPairs.add(errorTSLoadingConfigFailedField.with(time))
    }
    loadingConfigFailedEvent.log(eventPairs)
  }

  fun logFileToSendCalculated(totalFilesCount: Int, maxSentFilesCount: Int, sentFilesCount: Int) {
    sentFilesCountCalculated.log(totalFilesCount, maxSentFilesCount,sentFilesCount)
  }

  fun logDeletedFilesCalculated(totalFilesCount: Int, deletedFilesCount: Int, failedDeletingFilesCount: Int) {
    deletedFilesCalculated.log(totalFilesCount, deletedFilesCount, failedDeletingFilesCount)
  }

  fun logFileMetricsCalculated(fileSizeBytes: Long, eventsCount: Int,) {
    fileMetricsCalculated.log(fileSizeBytes, eventsCount)
  }

  fun logBacklogSnapshot(pendingFilesCount: Int, pendingTotalBytes: Long, oldestPendingFileWaitMs: Long) {
    backlogSnapshot.log(pendingFilesCount, pendingTotalBytes, oldestPendingFileWaitMs)
  }

  fun logFileDeleted(
    cause: FileDeletionCause,
    ageMs: Long,
    queuedMs: Long,
    sizeBytes: Long,
    buildType: EventLogBuildType,
  ) {
    fileDeletedEvent.log(
      deletedFileCauseField.with(cause),
      deletedFileAgeMsField.with(ageMs),
      deletedFileQueuedMsField.with(queuedMs),
      deletedFileSizeBytesField.with(sizeBytes),
      deletedFileBuildTypeField.with(buildType),
    )
  }

  fun logDictionaryListLoadFailed(error: EventLogMetadataUpdateError, retryCount: Int) {
    dictionaryListLoadFailed.log(loadFailedData(error, retryCount))
  }
  fun logDictionaryListUpdateFailed(error: EventLogMetadataUpdateError, retryCount: Int) {
    dictionaryListUpdateFailed.log(updateFailedData(error, retryCount))
  }
  fun logDictionaryLoaded(lastModified: Long, dictionaryName: String, retryCount: Int) {
    dictionaryLoadedEvent.log(lastModified, dictionaryName, retryCount)
  }
  fun logDictionaryLoadFailed(error: EventLogMetadataUpdateError, retryCount: Int, dictionaryName: String?) {
    dictionaryLoadFailedEvent.log(loadFailedData(error, retryCount, dictionaryName))
  }
  fun logDictionaryUpdated(lastModified: Long, dictionaryName: String, retryCount: Int) {
    dictionaryUpdatedEvent.log(lastModified, dictionaryName, retryCount)
  }
  fun logDictionaryUpdateFailed(error: EventLogMetadataUpdateError, retryCount: Int, dictionaryName: String?) {
    dictionaryUpdateFailedEvent.log(updateFailedData(error, retryCount, dictionaryName))
  }

  private fun loadFailedData(error: EventLogMetadataUpdateError, retryCount: Int, dictionaryName: String? = null): List<EventPair<*>> {
    val eventPairs = mutableListOf<EventPair<*>>(
      stageMetadataLoadFailedField.with(error.updateStage),
      errorMetadataLoadFailedField.with(error.errorType),
      codeMetadataLoadFailedField.with(error.errorCode),
      retryCountField.with(retryCount),
    )
    if (dictionaryName != null) {
      eventPairs.add(dictionaryNameField.with(dictionaryName))
    }
    return eventPairs
  }

  private fun updateFailedData(error: EventLogMetadataUpdateError, retryCount: Int, dictionaryName: String? = null): List<EventPair<*>> {
    val eventPairs = mutableListOf<EventPair<*>>(
      stageMetadataUpdateFailedField.with(error.updateStage),
      errorMetadataUpdateFailedField.with(error.errorType),
      codeMetadataUpdateFailedField.with(error.errorCode),
      retryCountField.with(retryCount),
    )
    if (dictionaryName != null) {
      eventPairs.add(dictionaryNameField.with(dictionaryName))
    }
    return eventPairs
  }

  companion object {
    private const val STAGE_METADATA_LOAD_FAILED_DESCRIPTION = "Indicates if metadata load has failed during loading stage (loading) or loaded " +
                                                               "metadata was invalid (parsing)."
    private const val ERROR_METADATA_LOAD_FAILED_DESCRIPTION = "The error name in case metadata load has failed. The error may occur during " +
                                                               "parsing metadata from local cache."
    private const val STAGE_METADATA_UPDATE_FAILED_DESCRIPTION = "Indicates if metadata update has failed during loading stage (loading) or " +
                                                                 "loaded metadata was invalid (parsing)."
    private const val ERROR_METADATA_UPDATE_FAILED_DESCRIPTION = "The error name in case metadata update has failed. An error may occur on " +
                                                                 "loading or parsing stages."
    private const val CODE_METADATA_UPDATE_FAILED_DESCRIPTION = "Response code if it was different from 200 (loading request failed)"
    private const val TOTAL_LOGS_SEND_DESCRIPTION = "Total amount of event log files ready to be sent"
    private const val SEND_LOGS_SEND_DESCRIPTION = "Amount of event log files attempted to send"
    private const val FAILED_LOGS_SEND_DESCRIPTION = "Amount of event log files which failed to send"
    private const val EXTERNAL_LOGS_SEND_DESCRIPTION = "Indicates if logs were being sent from external process or not."
    private const val SUCCEED_LOGS_SEND_DESCRIPTION = "The amount of successfully sent files"
    private const val ERRORS_LOGS_SEND_DESCRIPTION = "The list of integers which identify error codes. If error is less than 100, the problem is in data structure " +
                                                     "(e.g. invalid recorder code), if error is between 100 and 600 it's error code from an HTTP request."
    private const val SEND_TS_EXTERNAL_SEND_STARTED_DESCRIPTION = "Actual time when sending was started"
    private const val SEND_TS_EXTERNAL_SEND_FINISHED_DESCRIPTION = "Actual time when sending was finished"
    private const val SUCCEED_EXTERNAL_SEND_FINISHED_DESCRIPTION = "Shows if external process has sent data successfully or not"
    private const val ERROR_EXTERNAL_SEND_FINISHED_DESCRIPTION = "The error name in case the sending has failed, e.g. 'no arguments', 'not permitted server', 'no application config' etc."
    private const val SUCCEED_EXTERNAL_SEND_COMMAND_CREATION_FINISHED_DESCRIPTION = "Shows if the command to start an external upload process has " +
                                                                                    "finished successfully or not"
    private const val ERROR_EXTERNAL_SEND_COMMAND_CREATION_FINISHED_DESCRIPTION = "The error name in case the command to start an external upload " +
                                                                                  "process has failed, e.g. 'no logs', 'no temp folder' etc."
    private const val ERROR_TS_LOADING_CONFIG_FAILED_DESCRIPTION = "Error time stamp is added if error happened in external process"
    private val stageMetadataLoadFailedField = EventFields.Enum<EventLogMetadataUpdateStage>("stage",
                                                                                             STAGE_METADATA_LOAD_FAILED_DESCRIPTION)
    private val errorMetadataLoadFailedField = EventFields.String("error",
                                                                  EventLogMetadataParseException.EventLogMetadataParseErrorType.entries.plus(EventLogMetadataLoadException.EventLogMetadataLoadErrorType.entries).map { x -> x.name }.toList(),
                                                                  ERROR_METADATA_LOAD_FAILED_DESCRIPTION)
    private val codeMetadataLoadFailedField = EventFields.Int("code")
    private val stageMetadataUpdateFailedField = EventFields.Enum<EventLogMetadataUpdateStage>("stage", STAGE_METADATA_UPDATE_FAILED_DESCRIPTION)
    private val errorMetadataUpdateFailedField = EventFields.String("error",
                                                                    EventLogMetadataParseException.EventLogMetadataParseErrorType.entries.plus(EventLogMetadataLoadException.EventLogMetadataLoadErrorType.entries).map { x -> x.name }.toList(),
                                                                    ERROR_METADATA_UPDATE_FAILED_DESCRIPTION)
    private val codeMetadataUpdateFailedField = EventFields.Int("code", CODE_METADATA_UPDATE_FAILED_DESCRIPTION)
    private val retryCountField = EventFields.Int("retry_count", "Number of retries that the updater made so far in the current failure streak, " +
                                                                 "or -1 if unknown. It resets to 0 after a successful update cycle. " +
                                                                 "For dictionaries, it is counted per update cycle, not per dictionary.")
    private val dictionaryNameField = EventFields.StringValidatedByEnum("dictionary_name", "dictionary_name",
                                                                         "Name of the dictionary file, for example python_packages.ndjson")
    private val totalLogsSendField = EventFields.Int("total", TOTAL_LOGS_SEND_DESCRIPTION)
    private val sendLogsSendField = EventFields.Int("send", SEND_LOGS_SEND_DESCRIPTION)
    private val failedLogsSendField = EventFields.Int("failed", FAILED_LOGS_SEND_DESCRIPTION)
    private val externalLogsSendField = EventFields.Boolean("external", EXTERNAL_LOGS_SEND_DESCRIPTION)
    private val succeedLogsSendField = EventFields.Int("succeed", SUCCEED_LOGS_SEND_DESCRIPTION)
    private val pathsLogsField = EventFields.AnonymizedList("paths")
    private val errorsLogsSendField = EventFields.IntList("errors", ERRORS_LOGS_SEND_DESCRIPTION)
    private val sendTSExternalSendStarted = EventFields.Long("send_ts", SEND_TS_EXTERNAL_SEND_STARTED_DESCRIPTION)
    private val sendTSExternalSendFinishedField = EventFields.Long("send_ts", SEND_TS_EXTERNAL_SEND_FINISHED_DESCRIPTION)
    private val succeedExternalSendFinishedField = EventFields.Boolean("succeed", SUCCEED_EXTERNAL_SEND_FINISHED_DESCRIPTION)
    private val errorExternalSendFinishedField = EventFields.String("error",
                                                                    StatisticsResult.ResultCode.entries.plus(EventLogExternalSendConfig.ParseErrorType.entries).map { x -> x.name }.toList(),
                                                                    ERROR_EXTERNAL_SEND_FINISHED_DESCRIPTION)
    private val succeedExternalSendCommandCreationFinishedField = EventFields.Boolean("succeed",
                                                                                      SUCCEED_EXTERNAL_SEND_COMMAND_CREATION_FINISHED_DESCRIPTION)
    private val errorExternalSendCommandCreationFinishedField = EventFields.Enum<EventLogUploadErrorType>("error",
                                                                                                          ERROR_EXTERNAL_SEND_COMMAND_CREATION_FINISHED_DESCRIPTION)
    private val errorLoadingConfigFailedField = EventFields.StringValidatedByCustomRule("error", ClassNameRuleValidator::class.java)
    private val errorTSLoadingConfigFailedField = EventFields.Long("error_ts", ERROR_TS_LOADING_CONFIG_FAILED_DESCRIPTION)
    private val totalFilesCount = EventFields.Int("total_files_count", "The total number of log files")
    private val maxSentFilesCount = EventFields.Int("max_sent_files_count", "The max number of log files which are acceptable for sending")
    private val sentFilesCount = EventFields.Int("sent_files_count", "The number of log files which are ready for sending")
    private val deletedFilesCount = EventFields.Int("deleted_files_count", "The number of deleting log files")
    private val failedDeletingFilesCount = EventFields.Int("failed_deleting_files_count", "The number of log files which were failed to delete")
    private val fileSizeBytes = EventFields.Long("file_size_bytes", "File size in bytes")
    private val eventsCount = EventFields.Int("events_count", "Number of events in the file")
    private val pendingFilesCountField = EventFields.Int("pending_files_count", "Number of log files waiting to be sent")
    private val pendingTotalBytesField = EventFields.Long("pending_total_bytes", "Total size on disk of the pending log files, in bytes")
    private val oldestPendingFileWaitMsField = EventFields.Long("oldest_pending_file_wait_ms", "How long the longest-waiting pending file has been idle: now minus its last-modified time, in milliseconds")

    private val deletedFileCauseField = EventFields.Enum<FileDeletionCause>("deleted_file_cause", "Why the file was deleted: aged out, storage size cap exceeded, disk almost full, or rejected by the server on send (HTTP 400 / failed validation).")
    private val deletedFileAgeMsField = EventFields.Long("deleted_file_age_ms", "Age of the data in the deleted file: now minus the timestamp of its oldest event, in milliseconds (-1 if unknown)")
    private val deletedFileQueuedMsField = EventFields.Long("deleted_file_queued_ms", "How long the deleted file waited since its last write before being deleted: now minus last modified time, in milliseconds")
    private val deletedFileSizeBytesField = EventFields.Long("deleted_file_size_bytes", "Size of the deleted file in bytes")
    private val deletedFileBuildTypeField = EventFields.Enum<EventLogBuildType>("deleted_file_build_type", "Build type (EAP, RELEASE, etc.)")
  }
}
