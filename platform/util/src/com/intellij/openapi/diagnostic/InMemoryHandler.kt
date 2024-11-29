// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.logging.Handler
import java.util.logging.LogRecord

@ApiStatus.Internal
class InMemoryHandler(val outputPath: Path) : Handler() {
  companion object {
    const val FAILED_BUILD_LOG_FILE_NAME_PREFIX: String = "failed-build_";
    const val MAX_REPORT_NUMBER: Int = 3;
  }
  var buildFailed: Boolean = false

  private val messages = mutableListOf<String>()
  init {
    removeOldFiles(outputPath.parent.toString())
  }

  override fun publish(record: LogRecord?) {
    try {
      formatter.format(record)?.let {
        if (it.contains("Compiled with errors")) buildFailed = true
        messages.add(it)
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  override fun flush() {
    // Do nothing on a flush
  }

  override fun close() {
    if (buildFailed) {
      val outputFile = File(outputPath.toString())
      if(outputFile.exists()) {
        outputFile.delete()
      } else if(outputFile.parentFile.exists() == false) {
        outputFile.parentFile.mkdirs()
      }
      outputFile.createNewFile()

      messages.joinToString("").let {
        outputFile.appendText(it)
      }
    }
    messages.clear()
    buildFailed = false
  }

  private fun removeOldFiles(directoryPath: String) {
    val directory = File(directoryPath).also {
      if(!it.exists()) return
    }
    val logFiles = directory.listFiles { _, name -> name.startsWith(FAILED_BUILD_LOG_FILE_NAME_PREFIX) }
      .takeIf { it.size > MAX_REPORT_NUMBER } ?: return

    logFiles.let {
      val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
      it.sortBy { file ->
        val datePart = file.name.removePrefix(FAILED_BUILD_LOG_FILE_NAME_PREFIX).removeSuffix(".log")
        dateFormat.parse(datePart)
      }

      for (i in 0 until (it.size - MAX_REPORT_NUMBER)) it[i].delete()
    }
  }

  @TestOnly
  fun removeOldFilesForTests(directoryPath: String) = removeOldFiles(directoryPath)
}
