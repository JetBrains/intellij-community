// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.openapi.util.io.NioFiles
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.nio.file.Paths
import java.util.logging.Handler
import java.util.logging.LogRecord
import kotlin.io.path.*

@ApiStatus.Internal
class InMemoryHandler(val outputPath: Path) : Handler() {
  companion object {
    const val FAILED_BUILD_LOG_FILE_NAME_PREFIX: String = "failed-build_"
    const val MAX_REPORT_NUMBER: Int = 3
    const val IN_MEMORY_LOGGER_ADVANCED_SETTINGS_NAME: String = "compiler.inMemoryLogger"
  }

  var buildFailed: Boolean = false

  private val messages = mutableListOf<String>()

  init {
    removeOldFiles(outputPath.parent)
  }

  override fun publish(record: LogRecord?) {
    try {
      formatter.format(record)?.let {
        if (it.contains("Compiled with errors")) buildFailed = true
        messages.add(it)
      }
    }
    catch (e: Exception) {
      e.printStackTrace()
    }
  }

  override fun flush() {
    // Do nothing on a flush
  }

  override fun close() {
    if (buildFailed) {
      val outputFile = Paths.get(outputPath.toString())
      if (outputFile.exists()) {
        outputFile.deleteExisting()
      }
      else if (!outputFile.parent.exists()) {
        outputFile.createParentDirectories()
      }
      outputFile.createFile()

      messages.joinToString("").let {
        outputFile.appendText(it)
      }
    }
    messages.clear()
    buildFailed = false
  }

  private fun removeOldFiles(directory: Path) {
    val logFiles = NioFiles.list(directory).filter { it.name.startsWith(FAILED_BUILD_LOG_FILE_NAME_PREFIX) }
    if (logFiles.size >= MAX_REPORT_NUMBER) {
      logFiles
        .sortedBy { it.getLastModifiedTime() }
        .take(logFiles.size - MAX_REPORT_NUMBER + 1)
        .forEach { it.deleteIfExists() }
    }
  }
}
