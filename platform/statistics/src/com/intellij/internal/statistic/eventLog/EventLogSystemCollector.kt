// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.collectors.fus.ClassNameRuleValidator
import com.intellij.internal.statistic.eventLog.connection.StatisticsResult
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataLoadException
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataParseException
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUpdateError
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUpdateStage
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId3
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.uploader.EventLogUploadException.EventLogUploadErrorType
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.uploader.EventLogExternalSendConfig
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus
import java.util.*

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
internal class EventLogSystemCollector(eventLoggerProvider: StatisticsEventLoggerProvider) : CounterUsagesCollector() {
  private val id = "${eventLoggerProvider.recorderId.lowercase(Locale.ENGLISH)}.event.log"
  private val GROUP = EventLogGroup(id,
                                    // Increase the group's versions locally
                                    // and not increase the versions in all StatisticsEventLoggerProvider
                                    // in case of any changes in the groups
                                    eventLoggerProvider.version + 1,
                                    eventLoggerProvider.recorderId)
  override fun getGroup(): EventLogGroup = GROUP

  private val metadataLoadedEvent = GROUP.registerEvent("metadata.loaded", EventFields.Version, METADATA_LOADED_DESCRIPTION)
  private val metadataUpdatedEvent = GROUP.registerEvent("metadata.updated", EventFields.Version, METADATA_UPDATED_DESCRIPTION)
  private val metadataLoadFailedEvent = GROUP.registerEvent("metadata.load.failed",
                                                            stageMetadataLoadFailedField,
                                                            errorMetadataLoadFailedField,
                                                            codeMetadataLoadFailedField,
                                                            METADATA_LOAD_FAILED_DESCRIPTION)
  private val metadataUpdateFailedEvent = GROUP.registerEvent("metadata.update.failed",
                                                              stageMetadataUpdateFailedField,
                                                              errorMetadataUpdateFailedField,
                                                              codeMetadataUpdateFailedField,
                                                              METADATA_UPDATE_FAILED_DESCRIPTION)
  private val logsSendEvent = GROUP.registerVarargEvent("logs.send",
                                                        LOGS_SEND_DESCRIPTION,
                                                        totalLogsSendField,
                                                        sendLogsSendField,
                                                        failedLogsSendField,
                                                        externalLogsSendField,
                                                        pathsLogsField,
                                                        succeedLogsSendField,
                                                        errorsLogsSendField)
  private val externalSendStartedEvent = GROUP.registerEvent("external.send.started",
                                                             sendTSExternalSendStarted,
                                                             EXTERNAL_SEND_STARTED_DESCRIPTION)
  private val externalSendFinishedEvent = GROUP.registerEvent("external.send.finished",
                                                              sendTSExternalSendFinishedField,
                                                              succeedExternalSendFinishedField,
                                                              errorExternalSendFinishedField,
                                                              EXTERNAL_SEND_FINISHED_DESCRIPTION)
  private val externalSendCommandCreationStartedEvent = GROUP.registerEvent("external.send.command.creation.started",
                                                                            EXTERNAL_SEND_COMMAND_CREATION_STARTED_DESCRIPTION)
  private val externalSendCommandCreationFinishedEvent = GROUP.registerVarargEvent("external.send.command.creation.finished",
                                                                                   EXTERNAL_SEND_COMMAND_CREATION_FINISHED_DESCRIPTION,
                                                                                   succeedExternalSendCommandCreationFinishedField,
                                                                                   errorExternalSendCommandCreationFinishedField)
  private val loadingConfigFailedEvent = GROUP.registerVarargEvent("loading.config.failed",
                                                                   LOADING_CONFIG_FAILED_DESCRIPTION,
                                                                   errorLoadingConfigFailedField,
                                                                   errorTSLoadingConfigFailedField)

  private val sentFilesCountCalculated: EventId3<Int, Int, Int> = GROUP.registerEvent("sent.logs.files.calculated",
                                                                                 totalFilesCount,
                                                                                 maxSentFilesCount,
                                                                                 sentFilesCount,
                                                                                 "Calculate the count of logs files to send"
  )

  fun logMetadataLoaded(version: String?) = metadataLoadedEvent.log(version)
  fun logMetadataUpdated(version: String?) = metadataUpdatedEvent.log(version)
  fun logMetadataLoadFailed(error: EventLogMetadataUpdateError) {
    metadataLoadFailedEvent.log(error.updateStage, error.errorType, error.errorCode)
  }

  fun logMetadataUpdateFailed(error: EventLogMetadataUpdateError) {
    metadataUpdateFailedEvent.log(error.updateStage, error.errorType, error.errorCode)
  }

  fun logFilesSend(total: Int,
                   succeed: Int,
                   failed: Int,
                   external: Boolean,
                   successfullySentFiles: List<String>,
                   errors: List<Int>) =
    logsSendEvent.log(totalLogsSendField.with(total),
                      sendLogsSendField.with(succeed + failed),
                      failedLogsSendField.with(failed),
                      externalLogsSendField.with(external),
                      pathsLogsField.with(successfullySentFiles),
                      succeedLogsSendField.with(succeed),
                      errorsLogsSendField.with(errors)
    )


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

  companion object {
    private const val METADATA_LOADED_DESCRIPTION = "The metric is recorded in case the metadata was loaded"
    private const val METADATA_UPDATED_DESCRIPTION = "The metric is recorded in case the metadata was updated"
    private const val STAGE_METADATA_LOAD_FAILED_DESCRIPTION = "Indicates if metadata load was failed during loading stage (loading) or loaded " +
                                                               "metadata was invalid (parsing)."
    private const val ERROR_METADATA_LOAD_FAILED_DESCRIPTION = "The error name in case the metadata load was failed. The error may occur during " +
                                                               "parsing metadata from local cache."
    private const val METADATA_LOAD_FAILED_DESCRIPTION = "The event is recorded when IDE can't load metadata from a local cache. Local metadata " +
                                                         "is loaded on IDE start or on an explicit test action."
    private const val STAGE_METADATA_UPDATE_FAILED_DESCRIPTION = "Indicates if metadata update was failed during loading stage (loading) or " +
                                                                 "loaded metadata was invalid (parsing)."
    private const val ERROR_METADATA_UPDATE_FAILED_DESCRIPTION = "The error name in case the metadata update was failed, an error may occur on " +
                                                                 "loading or parsing stages."
    private const val CODE_METADATA_UPDATE_FAILED_DESCRIPTION = "In case loading request failed - the metric is recorded response code if it " +
                                                                "was different from 200."
    private const val METADATA_UPDATE_FAILED_DESCRIPTION = "The event is recorded when IDE can't update metadata. Update metadata can be " +
                                                           "triggered by a scheduler or via an explicit test action."
    private const val TOTAL_LOGS_SEND_DESCRIPTION = "Total amount of existing for sending event-log files"
    private const val SEND_LOGS_SEND_DESCRIPTION = "Amount of event log files attempted to send"
    private const val FAILED_LOGS_SEND_DESCRIPTION = "Amount of event-log files which were failed to send"
    private const val EXTERNAL_LOGS_SEND_DESCRIPTION = "Indicates if logs were sending from external process or not."
    private const val SUCCEED_LOGS_SEND_DESCRIPTION = "The amount of successfully sent files."
    private const val ERRORS_LOGS_SEND_DESCRIPTION = "The list of integers which identify error codes. If error is less than 100, the problem is " +
                                                     "in data structure (e.g. invalid recorder code), if error is between 100 and 600 it's error " +
                                                     "code from an HTTP request."
    private const val LOGS_SEND_DESCRIPTION = "The metric is recorded the sending or attempt to send the statistics event logs and the " +
                                              "corresponding attributes, e.g. number of total amount of files, number of sent files or amount of " +
                                              "files which were failed to send."
    private const val SEND_TS_EXTERNAL_SEND_STARTED_DESCRIPTION = "Actual time when sending was started"
    private const val EXTERNAL_SEND_STARTED_DESCRIPTION = "Indicates that external process of sending data was started. Note: the time of the " +
                                                          "event doesn't correspond to a real event time because it's recorded when IDE is " +
                                                          "opened after external send."
    private const val SEND_TS_EXTERNAL_SEND_FINISHED_DESCRIPTION = "Actual time when sending was finished"
    private const val SUCCEED_EXTERNAL_SEND_FINISHED_DESCRIPTION = "Shows if external process sent data successfully or not"
    private const val ERROR_EXTERNAL_SEND_FINISHED_DESCRIPTION = "The error name in case the sending was failed, e.g. 'no arguments', 'not permitted server', 'no application config' etc."
    private const val EXTERNAL_SEND_FINISHED_DESCRIPTION = "Indicates that external process sent data or failed with an error. Note: the time of " +
                                                           "the event doesn't correspond to a real event time because it's recorded when IDE is " +
                                                           "opened after external send."
    private const val EXTERNAL_SEND_COMMAND_CREATION_STARTED_DESCRIPTION = "The event is recorded on IDE close when we start creating a command " +
                                                                           "to start external upload process."
    private const val SUCCEED_EXTERNAL_SEND_COMMAND_CREATION_FINISHED_DESCRIPTION = "Shows if command to start external upload process was " +
                                                                                    "finished successfully or not"
    private const val ERROR_EXTERNAL_SEND_COMMAND_CREATION_FINISHED_DESCRIPTION = "The error name in case command to start external upload " +
                                                                                  "process was failed, e.g. 'no logs', 'no temp folder' etc."
    private const val EXTERNAL_SEND_COMMAND_CREATION_FINISHED_DESCRIPTION = "The event is recorded when we created a command and ready to start " +
                                                                            "it or when creation failed with an error."
    private const val ERROR_TS_LOADING_CONFIG_FAILED_DESCRIPTION = "Error time stamp is added if error happened in external process"
    private const val LOADING_CONFIG_FAILED_DESCRIPTION = "Event is recorded if loading config (i.e. send entrypoint, metadata url, etc) failed."
    private val stageMetadataLoadFailedField = EventFields.Enum<EventLogMetadataUpdateStage>("stage",
                                                                                             STAGE_METADATA_LOAD_FAILED_DESCRIPTION)
    private val errorMetadataLoadFailedField = EventFields.String("error",
                                                                  EventLogMetadataParseException.EventLogMetadataParseErrorType.entries.map { x -> x.name }.toList(),
                                                                  ERROR_METADATA_LOAD_FAILED_DESCRIPTION)
    private val codeMetadataLoadFailedField = EventFields.Int("code")
    private val stageMetadataUpdateFailedField = EventFields.Enum<EventLogMetadataUpdateStage>("stage", STAGE_METADATA_UPDATE_FAILED_DESCRIPTION)
    private val errorMetadataUpdateFailedField = EventFields.String("error",
                                                                    EventLogMetadataParseException.EventLogMetadataParseErrorType.entries.plus(EventLogMetadataLoadException.EventLogMetadataLoadErrorType.entries).map { x -> x.name }.toList(),
                                                                    ERROR_METADATA_UPDATE_FAILED_DESCRIPTION)
    private val codeMetadataUpdateFailedField = EventFields.Int("code", CODE_METADATA_UPDATE_FAILED_DESCRIPTION)
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
    private val totalFilesCount = EventFields.Int("total_files_count", "The total logs files count")
    private val maxSentFilesCount = EventFields.Int("max_sent_files_count", "The max sent logs files count")
    private val sentFilesCount = EventFields.Int("sent_files_count", "The sent logs files count")
  }
}