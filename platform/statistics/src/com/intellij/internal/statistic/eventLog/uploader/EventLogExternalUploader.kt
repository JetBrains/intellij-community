// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.uploader

import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.*
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.io.exists
import java.io.File
import java.nio.file.Paths

object EventLogExternalUploader {
  private val LOG = Logger.getInstance(EventLogExternalUploader.javaClass)
  private const val UPLOADER_MAIN_CLASS = "com.intellij.internal.statistic.uploader.EventLogUploader"

  fun startExternalUpload(recorderId: String, isTest: Boolean, shouldCopy: Boolean) {
    val recorder = EventLogInternalRecorderConfig(recorderId)
    if (!recorder.isSendEnabled()) {
      LOG.info("Don't start external process because sending logs is disabled")
      return
    }

    val device = DeviceConfiguration(EventLogConfiguration.deviceId, EventLogConfiguration.bucket)
    val application = EventLogInternalApplicationInfo(isTest)
    try {
      val command = prepareUploadCommand(device, recorder, application, shouldCopy)
      Runtime.getRuntime().exec(command)
      LOG.info("Started external process for uploading event log")
    }
    catch (e: EventLogUploadException) {
      LOG.info(e)
    }
  }

  private fun prepareUploadCommand(device: DeviceConfiguration,
                                   recorder: EventLogRecorderConfig,
                                   applicationInfo: EventLogApplicationInfo,
                                   shouldCopy: Boolean): Array<out String> {
    val logFiles = logsToSend(recorder)
    if (logFiles.isEmpty()) {
      throw EventLogUploadException("No available logs to send")
    }

    val tempDir = getTempDir()
    if (shouldCopy && FileUtil.isAncestor(PathManager.getHomePath(), tempDir.path, true)) {
      throw EventLogUploadException("Temp directory inside installation: $tempDir")
    }
    val uploader = findUploader()
    val libs = findLibsByPrefixes(
      "kotlin-stdlib", "gson", "commons-logging", "log4j.jar", "httpclient", "httpcore", "httpmime", "jdom.jar", "annotations.jar"
    )

    val uploaderCopy = if (shouldCopy) uploader.copyTo(File(tempDir, uploader.name), true) else uploader
    val libCopies = if (shouldCopy) libs.map {it.copyTo(File(tempDir, it.name), true)}.map { it.path } else libs.map { it.path }
    val classpath = joinAsClasspath(libCopies, uploaderCopy)

    val args = arrayListOf<String>()
    val java = findOrCopyJava(tempDir, shouldCopy)
    args += File(java, if (SystemInfo.isWindows) "bin\\java.exe" else "bin/java").path
    addArgument(args, "-cp", classpath)

    args += "-Djava.io.tmpdir=${tempDir.path}"
    args += UPLOADER_MAIN_CLASS

    addArgument(args, RECORDER_OPTION, recorder.getRecorderId())
    val joinedPath: String = logFiles.joinToString(separator = File.pathSeparator)
    addArgument(args, LOGS_OPTION, joinedPath)
    addArgument(args, DEVICE_OPTION, device.deviceId)
    addArgument(args, BUCKET_OPTION, device.bucket.toString())
    addArgument(args, URL_OPTION, applicationInfo.templateUrl)
    addArgument(args, PRODUCT_OPTION, applicationInfo.productCode)

    if (applicationInfo.isInternal) {
      args += INTERNAL_OPTION
    }

    if (applicationInfo.isTest) {
      args += TEST_OPTION
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
    throw EventLogUploadException("Cannot find uploader jar")
  }

  private fun findOrCopyJava(tempDir: File, shouldCopy: Boolean): String {
    var java = System.getProperty("java.home")
    val jrePath = Paths.get(java)
    val idePath = Paths.get(PathManager.getHomePath()).toRealPath()
    if (jrePath.startsWith(idePath) && shouldCopy) {
      val javaCopy = File(tempDir, "jre")
      if (javaCopy.exists()) FileUtil.delete(javaCopy)
      FileUtil.copyDir(File(java), javaCopy)
      java = javaCopy.path
    }
    return java
  }

  private fun findLibsByPrefixes(vararg prefixes: String): Array<File> {
    val lib = PathManager.getLibPath()
    val libFiles = File(lib).listFiles { file -> startsWithAny(file.name, prefixes) }
    if (libFiles == null || libFiles.isEmpty()) {
      throw EventLogUploadException("Cannot find libraries from dependency for event log uploader")
    }
    return libFiles
  }

  private fun startsWithAny(str: String, prefixes: Array<out String>): Boolean {
    for (prefix in prefixes) {
      if (str.startsWith(prefix)) return true
    }
    return false
  }

  private fun getTempDir(): File {
    val tempDir = File(PathManager.getTempPath(), "statistics-uploader")
    if (!(tempDir.exists() || tempDir.mkdirs())) {
      throw EventLogUploadException("Cannot create temp directory: $tempDir")
    }
    return tempDir
  }
}
