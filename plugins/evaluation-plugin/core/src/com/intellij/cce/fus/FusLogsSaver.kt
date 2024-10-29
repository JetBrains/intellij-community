package com.intellij.cce.fus

import com.intellij.cce.workspace.storages.LogsSaver
import com.intellij.internal.statistic.eventLog.LogEventSerializer
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.util.io.delete
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Saves FUS logs of the action that will be passed in [invokeRememberingLogs] to [finalStorageDir].
 * In the resulting directory, logs will be divided into files by groups.
 */
class FusLogsSaver(private val finalStorageDir: Path, private val allowedGroups: List<String>? = null) : LogsSaver {
  private val temporaryFusLogsDirectory: Path = PathManager.getSystemPath().toNioPathOrNull()!! / "completion-fus-logs"

  init {
    if (temporaryFusLogsDirectory.exists()) {
      clearTemporaryStorage()
    }
    temporaryFusLogsDirectory.createDirectory()
    require(finalStorageDir.exists())
  }

  override fun <T> invokeRememberingLogs(action: () -> T): T {
    val logFilter: (LogEvent) -> Boolean = if (allowedGroups != null) { log -> log.group.id in allowedGroups }
    else { _ -> true }

    val (actionResult, fusLogs) = collectingFusLogs(logFilter, action)
    val fusLogsByGroup = fusLogs.groupBy { it.group }
    for ((eventGroup, groupLogs) in fusLogsByGroup) {
      val serialisedGroupLogs = groupLogs.joinToString("\n") { LogEventSerializer.toString(it) }
      val fusGroupPath = temporaryFusLogsDirectory / eventGroup.id
      fusGroupPath.toFile().appendText(serialisedGroupLogs + "\n")
    }
    return actionResult
  }

  @OptIn(ExperimentalPathApi::class)
  override fun save(languageName: String?, trainingPercentage: Int) {
    try {
      for (sessionTemporaryRelativePath in temporaryFusLogsDirectory.walk()) {
        val sessionTemporaryPath = temporaryFusLogsDirectory / sessionTemporaryRelativePath
        val sessionId = sessionTemporaryPath.fileName
        val sessionFinalPath = obtainFinalLogsDirectory(languageName) / sessionId
        Files.move(sessionTemporaryPath, sessionFinalPath)
      }
    }
    finally {
      clearTemporaryStorage()
    }
  }

  private fun obtainFinalLogsDirectory(languageName: String?): Path {
    val fusFinalDirectory = if (languageName != null) finalStorageDir / languageName else finalStorageDir
    if (!fusFinalDirectory.exists()) {
      fusFinalDirectory.createDirectory()
    }
    else {
      require(fusFinalDirectory.isDirectory())
    }
    return fusFinalDirectory
  }

  private fun clearTemporaryStorage() {
    require(temporaryFusLogsDirectory.exists())
    temporaryFusLogsDirectory.delete(true)
  }
}
