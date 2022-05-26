// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.uploader

import com.google.gson.Gson
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupsFilterRules
import com.intellij.internal.statistic.eventLog.uploader.EventLogUploadException.EventLogUploadErrorType.*
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.*
import com.intellij.internal.statistic.uploader.events.*
import com.intellij.internal.statistic.uploader.util.ExtraHTTPHeadersParser
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ArrayUtil
import org.jetbrains.annotations.NotNull
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object EventLogExternalUploader {
  private val LOG = Logger.getInstance(EventLogExternalUploader.javaClass)
  private const val UPLOADER_MAIN_CLASS = "com.intellij.internal.statistic.uploader.EventLogUploader"

  fun logPreviousExternalUploadResult(recorderId: String) {
    val recorder = EventLogInternalRecorderConfig(recorderId)
    if (!recorder.isSendEnabled()) {
      return
    }

    try {
      val tempDir = getTempFile()
      if (tempDir.exists()) {
        val events = ExternalEventsLogger.parseEvents(tempDir)
        for (event in events) {
          when (event) {
            is ExternalUploadStartedEvent -> {
              EventLogSystemLogger.logStartingExternalSend(recorderId, event.timestamp)
            }
            is ExternalUploadSendEvent -> {
              val files = event.successfullySentFiles
              val errors = event.errors
              EventLogSystemLogger.logFilesSend(recorderId, event.total, event.succeed, event.failed, true, files, errors)
            }
            is ExternalUploadFinishedEvent -> {
              EventLogSystemLogger.logFinishedExternalSend(recorderId, event.error, event.timestamp)
            }
            is ExternalSystemErrorEvent -> {
              EventLogSystemLogger.logSystemError(recorderId, event.event, event.errorClass, event.timestamp)
            }
          }
        }
      }
      tempDir.deleteRecursively()
    }
    catch (e: Exception) {
      LOG.warn("Failed reading previous upload result: " + e.message)
    }
  }

  fun startExternalUpload(recorderId: String, isTest: Boolean) {
    val recorder = EventLogInternalRecorderConfig(recorderId, false)
    if (!recorder.isSendEnabled()) {
      LOG.info("Don't start external process because sending logs is disabled")
      return
    }

    EventLogSystemLogger.logCreatingExternalSendCommand(recorderId)
    val config = EventLogConfiguration.getInstance().getOrCreate(recorderId)
    val application = EventLogInternalApplicationInfo(recorderId, isTest)
    try {
      val command = prepareUploadCommand(config, recorder, application)
      EventLogSystemLogger.logFinishedCreatingExternalSendCommand(recorderId, null)
      Runtime.getRuntime().exec(command)
      LOG.info("Started external process for uploading event log")
    }
    catch (e: EventLogUploadException) {
      EventLogSystemLogger.logFinishedCreatingExternalSendCommand(recorderId, e.errorType)
      LOG.info(e)
    }
  }

  private fun prepareUploadCommand(config: EventLogRecorderConfiguration,
                                   recorder: EventLogRecorderConfig,
                                   applicationInfo: EventLogApplicationInfo): Array<out String> {
    val logFiles = recorder.getFilesToSendProvider().getFilesToSend().map { it.file.absolutePath }
    if (logFiles.isEmpty()) {
      throw EventLogUploadException("No available logs to send", NO_LOGS)
    }

    val tempDir = getOrCreateTempDir()
    val uploader = findUploader()

    val libPaths = ArrayList<String>()
    libPaths.add(findLibraryByClass(kotlin.coroutines.Continuation::class.java)) // add kotlin-std to classpath
    libPaths.add(findLibraryByClass(NotNull::class.java))
    libPaths.add(findLibraryByClass(Gson::class.java))
    libPaths.add(findLibraryByClass(EventGroupsFilterRules::class.java))
    val classpath = joinAsClasspath(libPaths, uploader)

    val args = arrayListOf<String>()
    val java = findJavaHome()
    args += File(java, if (SystemInfo.isWindows) "bin\\java.exe" else "bin/java").path
    addArgument(args, "-cp", classpath)

    args += "-Djava.io.tmpdir=${tempDir.path}"
    args += UPLOADER_MAIN_CLASS

    addArgument(args, IDE_TOKEN, Paths.get(PathManager.getSystemPath(), "token").toAbsolutePath().toString())
    addArgument(args, RECORDER_OPTION, recorder.getRecorderId())

    addArgument(args, LOGS_OPTION, logFiles.joinToString(separator = File.pathSeparator))
    addArgument(args, DEVICE_OPTION, config.deviceId)
    addArgument(args, BUCKET_OPTION, config.bucket.toString())
    addArgument(args, MACHINE_ID_OPTION, config.machineId.id)
    addArgument(args, ID_REVISION_OPTION, config.machineId.revision.toString())
    addArgument(args, URL_OPTION, applicationInfo.templateUrl)
    addArgument(args, PRODUCT_OPTION, applicationInfo.productCode)
    addArgument(args, PRODUCT_VERSION_OPTION, applicationInfo.productVersion)
    addArgument(args, USER_AGENT_OPTION, applicationInfo.connectionSettings.getUserAgent())
    addArgument(args, EXTRA_HEADERS, ExtraHTTPHeadersParser.serialize(applicationInfo.connectionSettings.getExtraHeaders()))

    if (applicationInfo.isInternal) {
      args += INTERNAL_OPTION
    }

    if (applicationInfo.isTest) {
      args += TEST_OPTION
    }

    if (applicationInfo.isEAP) {
      args += EAP_OPTION
    }
    return ArrayUtil.toStringArray(args)
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
      Paths.get(PathManager.getHomePath(), "out/artifacts/statistics-uploader.jar")
    }
    else {
      PathManager.getJarForClass(EventLogUploaderOptions::class.java)
    }

    if (uploader == null || !Files.isRegularFile(uploader)) {
      throw EventLogUploadException("Cannot find uploader jar", NO_UPLOADER)
    }
    return uploader
  }

  private fun findLibraryByClass(clazz: Class<*>): String {
    val library = PathManager.getJarForClass(clazz)

    if (library == null || !Files.isRegularFile(library)) {
      throw EventLogUploadException("Cannot find jar for $clazz", NO_UPLOADER)
    }
    return library.toString()
  }

  private fun findJavaHome(): String {
    return System.getProperty("java.home")
  }

  private fun getOrCreateTempDir(): File {
    val tempDir = getTempFile()
    if (!(tempDir.exists() || tempDir.mkdirs())) {
      throw EventLogUploadException("Cannot create temp directory: $tempDir", NO_TEMP_FOLDER)
    }
    return tempDir
  }

  private fun getTempFile(): File {
    return File(PathManager.getTempPath(), "statistics-uploader")
  }
}
