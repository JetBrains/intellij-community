// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.uploader

import com.fasterxml.jackson.annotation.JsonView
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.eventLog.EventLogApplicationInfo
import com.intellij.internal.statistic.eventLog.EventLogInternalApplicationInfo
import com.intellij.internal.statistic.eventLog.EventLogInternalSendConfig
import com.intellij.internal.statistic.eventLog.EventLogSendConfig
import com.intellij.internal.statistic.eventLog.EventLogSystemCollector
import com.intellij.internal.statistic.eventLog.LogSystemCollector
import com.intellij.internal.statistic.eventLog.LogSystemCollector.sendingForAllRecordersDisabledField
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupsFilterRules
import com.intellij.internal.statistic.eventLog.connection.metadata.StatsConnectionSettings
import com.intellij.internal.statistic.eventLog.uploader.EventLogUploadException.EventLogUploadErrorType.NO_LOGS
import com.intellij.internal.statistic.eventLog.uploader.EventLogUploadException.EventLogUploadErrorType.NO_TEMP_FOLDER
import com.intellij.internal.statistic.eventLog.uploader.EventLogUploadException.EventLogUploadErrorType.NO_UPLOADER
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.BASELINE_VERSION
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.BUCKET_OPTION
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.DEVICE_OPTION
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.EAP_OPTION
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.ESCAPING_OPTION
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.EXTRA_HEADERS
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.IDE_TOKEN
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.ID_REVISION_OPTION
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.INTERNAL_OPTION
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.LOGS_OPTION
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.MACHINE_ID_OPTION
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.PRODUCT_OPTION
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.PRODUCT_VERSION_OPTION
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.RECORDERS_OPTION
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.REGIONAL_CODE_OPTION
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.TEST_CONFIG
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.TEST_SEND_ENDPOINT
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.USER_AGENT_OPTION
import com.intellij.internal.statistic.uploader.events.ExternalEventsLogger
import com.intellij.internal.statistic.uploader.events.ExternalSystemErrorEvent
import com.intellij.internal.statistic.uploader.events.ExternalSystemEvent
import com.intellij.internal.statistic.uploader.events.ExternalUploadFinishedEvent
import com.intellij.internal.statistic.uploader.events.ExternalUploadSendEvent
import com.intellij.internal.statistic.uploader.events.ExternalUploadStartedEvent
import com.intellij.internal.statistic.uploader.util.ExtraHTTPHeadersParser
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.Strings
import com.intellij.util.ArrayUtil
import com.jetbrains.fus.reporting.MetadataStorage
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.NotNull
import tools.jackson.core.JsonParser
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.KotlinFeature
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.reflect.full.IllegalCallableAccessException

object EventLogExternalUploader {
  private val LOG = Logger.getInstance(EventLogExternalUploader.javaClass)
  private const val UPLOADER_MAIN_CLASS = "com.intellij.internal.statistic.uploader.EventLogUploader"

  fun logPreviousExternalUploadResult(providers: List<StatisticsEventLoggerProvider>) {
    val enableEventLogSystemCollectors = providers.filter { it.isSendEnabled() }.map { it.eventLogSystemLogger }
    if (enableEventLogSystemCollectors.isEmpty()) {
      return
    }

    try {
      val tempDir = getTempFile()
      if (tempDir.exists()) {
        val events = ExternalEventsLogger.parseEvents(tempDir)
        enableEventLogSystemCollectors.forEach { logPreviousExternalUploadResultByRecorder(it, events) }
      }
      tempDir.deleteRecursively()
    }
    catch (e: Exception) {
      LOG.warn("Statistics. External uploader. Failed reading previous upload result: " + e.message)
    }
  }

  private fun logPreviousExternalUploadResultByRecorder(eventLogSystemCollector: EventLogSystemCollector, events: List<ExternalSystemEvent>) {
    for (event in events) {
      val eventRecorderId = event.recorderId
      if (eventRecorderId != eventLogSystemCollector.group.recorder && eventRecorderId != ExternalSystemEvent.ALL_RECORDERS) {
        continue
      }

      when (event) {
        is ExternalUploadStartedEvent -> {
          eventLogSystemCollector.logStartingExternalSend(event.timestamp)
        }
        is ExternalUploadSendEvent -> {
          val files = event.successfullySentFiles
          val errors = event.errors
          eventLogSystemCollector.logFilesSend(event.total, event.succeed, event.failed, true, files, errors)
        }
        is ExternalUploadFinishedEvent -> {
          eventLogSystemCollector.logExternalSendFinished(event.error, event.timestamp)
        }
        is ExternalSystemErrorEvent -> {
          eventLogSystemCollector.logLoadingConfigFailed(event.errorClass, event.timestamp)
        }
      }
    }
  }

  fun startExternalUpload(recordersProviders: List<StatisticsEventLoggerProvider>, isTestConfig: Boolean, isTestSendEndpoint: Boolean) {
    val enabledEventLoggerProviders = recordersProviders.filter { it.isSendEnabled() }
    if (enabledEventLoggerProviders.isEmpty()) {
      LOG.info("Statistics. Don't start external uploader because sending logs is disabled for all recorders")
      LogSystemCollector.externalUploaderLaunched.log(sendingForAllRecordersDisabledField.with(true))
      return
    }
    enabledEventLoggerProviders.forEach { it.eventLogSystemLogger.logExternalSendCommandCreationStarted() }
    val application = EventLogInternalApplicationInfo(isTestConfig, isTestSendEndpoint)
    try {
      val command = prepareUploadCommand(enabledEventLoggerProviders, application)
      enabledEventLoggerProviders.forEach { it.eventLogSystemLogger.logExternalSendCommandCreationFinished(null) }

      if (LOG.isDebugEnabled) {
        LOG.debug("Statistics. Starting external uploader: '${command.joinToString(separator = " ")}'")
      }

      Runtime.getRuntime().exec(command)
      LOG.info("Statistics. Started external process for uploading event log")
    }
    catch (e: EventLogUploadException) {
      enabledEventLoggerProviders.forEach { it.eventLogSystemLogger.logExternalSendCommandCreationFinished(e.errorType) }
      LOG.info("Statistics. External uploader error: $e")
    }
  }

  private fun prepareUploadCommand(recorders: List<StatisticsEventLoggerProvider>, applicationInfo: EventLogApplicationInfo): Array<out String> {
    val sendConfigs = recorders.map { EventLogInternalSendConfig.createByRecorder(it.recorderId, false) }
    if (sendConfigs.isEmpty()) {
      throw EventLogUploadException("Statistics. External uploader. No available logs to send", NO_LOGS)
    }

    val tempDir = getOrCreateTempDir()
    val uploader = findUploader()

    val libPaths = setOf(
      findLibraryByClass(kotlin.coroutines.Continuation::class.java), // add kotlin-std to classpath
      findLibraryByClass(NotNull::class.java), // annotations
      findLibraryByClass(JsonParser::class.java), //add jackson-core
      findLibraryByClass(JsonNode::class.java), //add jackson-databind
      findLibraryByClass(JsonView::class.java), //add jackson-annotations
      findLibraryByClass(KotlinFeature::class.java), // add jackson-kotlin-module
      findLibraryByClass(IllegalCallableAccessException::class.java), // add kotlin-reflect
      findLibraryByClass(EventGroupsFilterRules::class.java), // validation library
      findLibraryByClass(StatsConnectionSettings::class.java), // com.jetbrains.fus.reporting.model
      findLibraryByClass(MetadataStorage::class.java), // com.jetbrains.fus.reporting.fus-api
      findLibraryByClass(Json::class.java), // kotlinx.serialization.json
      findLibraryByClass(StringFormat::class.java) // kotlinx.serialization
    )
    val classpath = joinAsClasspath(libPaths.toList(), uploader)

    return createExternalUploadCommand(applicationInfo, sendConfigs, classpath, tempDir)
  }

  fun createExternalUploadCommand(applicationInfo: EventLogApplicationInfo,
                                  configs: List<EventLogSendConfig>,
                                  classpath: String,
                                  tempDir: File): Array<out String> {
    val args = arrayListOf<String>()
    val java = findJavaHome()
    args += File(java, if (SystemInfo.isWindows) "bin\\java.exe" else "bin/java").path
    addArgument(args, "-cp", classpath)

    args += "-Djava.io.tmpdir=${tempDir.path}"
    //args += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005" Debug
    args += UPLOADER_MAIN_CLASS

    addArgument(args, IDE_TOKEN, Paths.get(PathManager.getSystemPath(), "token").toAbsolutePath().toString())

    addArgument(args, RECORDERS_OPTION, configs.joinToString(separator = ";") { it.getRecorderId() })
    for (config in configs) {
      addRecorderConfiguration(args, config)
    }

    addArgument(args, REGIONAL_CODE_OPTION, applicationInfo.regionalCode)
    addArgument(args, PRODUCT_OPTION, applicationInfo.productCode)
    addArgument(args, PRODUCT_VERSION_OPTION, applicationInfo.productVersion)
    addArgument(args, BASELINE_VERSION, applicationInfo.baselineVersion.toString())
    addArgument(args, USER_AGENT_OPTION, applicationInfo.connectionSettings.provideUserAgent())
    addArgument(args, EXTRA_HEADERS, ExtraHTTPHeadersParser.serialize(applicationInfo.connectionSettings.provideExtraHeaders()))

    if (applicationInfo.isInternal) {
      args += INTERNAL_OPTION
    }

    if (applicationInfo.isTestSendEndpoint) {
      args += TEST_SEND_ENDPOINT
    }

    if (applicationInfo.isTestConfig) {
      args += TEST_CONFIG
    }

    if (applicationInfo.isEAP) {
      args += EAP_OPTION
    }
    return ArrayUtil.toStringArray(args)
  }

  private fun addRecorderConfiguration(args: ArrayList<String>, config: EventLogSendConfig) {
    val recorderIdLowerCase = Strings.toLowerCase(config.getRecorderId())
    addArgument(args, DEVICE_OPTION + recorderIdLowerCase, config.getDeviceId())
    addArgument(args, BUCKET_OPTION + recorderIdLowerCase, config.getBucket().toString())
    addArgument(args, MACHINE_ID_OPTION + recorderIdLowerCase, config.getMachineId().id)
    addArgument(args, ID_REVISION_OPTION + recorderIdLowerCase, config.getMachineId().revision.toString())

    val filesToSend = config.getFilesToSendProvider().getFilesToSend().map { it.file }
    addArgument(args, LOGS_OPTION + recorderIdLowerCase, filesToSend.joinToString(separator = File.pathSeparator))

    addArgument(args, ESCAPING_OPTION + recorderIdLowerCase, config.isEscapingEnabled().toString())
  }

  private fun addArgument(args: ArrayList<String>, name: String, value: String) {
    args += name
    args += value
  }

  private fun joinAsClasspath(libCopies: List<String>, uploaderCopy: Path): String {
    if (libCopies.isEmpty()) {
      return uploaderCopy.toString()
    }
    val libClassPath = libCopies.joinToString(separator = File.pathSeparator)
    return "$libClassPath${File.pathSeparator}$uploaderCopy"
  }

  private fun findUploader(): Path {
    val uploader = if (PluginManagerCore.isRunningFromSources()) {
        //Build -> Build Artifacts -> statistics-uploader -> Build
        Paths.get(PathManager.getHomePath(), "out/artifacts/statistics-uploader.jar")
    }
    else {
      PathManager.getJarForClass(EventLogUploaderOptions::class.java)
    }

    if (uploader == null || !Files.isRegularFile(uploader)) {
      throw EventLogUploadException("Statistics. External uploader. Cannot find uploader jar", NO_UPLOADER)
    }
    return uploader
  }

  private fun findLibraryByClass(clazz: Class<*>): String {
    val library = PathManager.getJarForClass(clazz)

    if (library == null || !Files.isRegularFile(library)) {
      throw EventLogUploadException("Statistics. External uploader. Cannot find jar for $clazz", NO_UPLOADER)
    }
    return library.toString()
  }

  private fun findJavaHome(): String {
    return System.getProperty("java.home")
  }

  private fun getOrCreateTempDir(): File {
    val tempDir = getTempFile()
    if (!(tempDir.exists() || tempDir.mkdirs())) {
      throw EventLogUploadException("Statistics. External uploader. Cannot create temp directory: $tempDir", NO_TEMP_FOLDER)
    }
    return tempDir
  }

  private fun getTempFile(): File {
    return File(PathManager.getTempPath(), "statistics-uploader")
  }
}
