// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.uploader

import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.eventLog.uploader.EventLogUploadException.EventLogUploadErrorType.*
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.*
import com.intellij.internal.statistic.uploader.events.*
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ArrayUtil
import com.intellij.util.io.exists
import java.io.File
import java.lang.Exception
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
              EventLogSystemLogger.logFilesSend(recorderId, event.total, event.succeed, event.failed, true, event.successfullySentFiles)
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
    val recorder = EventLogInternalRecorderConfig(recorderId)
    if (!recorder.isSendEnabled()) {
      LOG.info("Don't start external process because sending logs is disabled")
      return
    }

    EventLogSystemLogger.logCreatingExternalSendCommand(recorderId)
    val device = DeviceConfiguration(EventLogConfiguration.deviceId, EventLogConfiguration.bucket)
    val application = EventLogInternalApplicationInfo(recorderId, isTest)
    try {
      val command = prepareUploadCommand(device, recorder, application)
      EventLogSystemLogger.logFinishedCreatingExternalSendCommand(recorderId, null)
      Runtime.getRuntime().exec(command)
      LOG.info("Started external process for uploading event log")
    }
    catch (e: EventLogUploadException) {
      EventLogSystemLogger.logFinishedCreatingExternalSendCommand(recorderId, e.errorType)
      LOG.info(e)
    }
  }

  private fun prepareUploadCommand(device: DeviceConfiguration,
                                   recorder: EventLogRecorderConfig,
                                   applicationInfo: EventLogApplicationInfo): Array<out String> {
    val logFiles = logsToSend(recorder)
    if (logFiles.isEmpty()) {
      throw EventLogUploadException("No available logs to send", NO_LOGS)
    }

    val tempDir = getOrCreateTempDir()
    val uploader = findUploader()
    val libs = findLibsByPrefixes(
      "platform-statistics-config.jar", "kotlin-stdlib", "gson", "commons-logging", "log4j.jar", "httpclient", "httpcore", "httpmime", "annotations.jar"
    )

    val libPaths = libs.map { it.path }
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
    addArgument(args, DEVICE_OPTION, device.deviceId)
    addArgument(args, BUCKET_OPTION, device.bucket.toString())
    addArgument(args, URL_OPTION, applicationInfo.templateUrl)
    addArgument(args, PRODUCT_OPTION, applicationInfo.productCode)
    addArgument(args, PRODUCT_VERSION_OPTION, applicationInfo.productVersion)
    addArgument(args, USER_AGENT_OPTION, applicationInfo.userAgent)

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

  private fun logsToSend(recorder: EventLogRecorderConfig): List<String> {
    val dir = recorder.getLogFilesProvider().getLogFilesDir()
    if (dir != null && dir.exists()) {
      return dir.toFile().listFiles()?.take(5)?.map { it.absolutePath } ?: emptyList()
    }
    return emptyList()
  }

  private fun joinAsClasspath(libCopies: List<String>, uploaderCopy: File): String {
    if (libCopies.isEmpty()) {
      return uploaderCopy.path
    }
    val libClassPath = libCopies.joinToString(separator = File.pathSeparator)
    return "$libClassPath${File.pathSeparator}${uploaderCopy.path}"
  }

  private fun findUploader(): File {
    val uploader = File(PathManager.getLibPath(), "platform-statistics-uploader.jar")
    if (uploader.exists() && !uploader.isDirectory) {
      return uploader
    }

    //consider local debug IDE case
    val localBuild = File(PathManager.getHomePath(), "out/artifacts/statistics-uploader.jar")
    if (localBuild.exists() && !localBuild.isDirectory) {
      return localBuild
    }
    throw EventLogUploadException("Cannot find uploader jar", NO_UPLOADER)
  }

  private fun findJavaHome(): String {
    return System.getProperty("java.home")
  }

  private fun findLibsByPrefixes(vararg prefixes: String): Array<File> {
    val lib = PathManager.getLibPath()
    val libFiles = File(lib).listFiles { file -> startsWithAny(file.name, prefixes) }
    if (libFiles == null || libFiles.isEmpty()) {
      throw EventLogUploadException("Cannot find libraries from dependency for event log uploader", NO_LIBRARIES)
    }
    return libFiles
  }

  private fun startsWithAny(str: String, prefixes: Array<out String>): Boolean {
    for (prefix in prefixes) {
      if (str.startsWith(prefix)) return true
    }
    return false
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
