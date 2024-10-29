// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.storages

import java.io.BufferedWriter
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPInputStream
import kotlin.io.path.div

class StatLogsSaver(private val logsTemporaryStoragePath: Path, private val finalStorageDir: Path) : LogsSaver {
  private val formatter = SimpleDateFormat("dd_MM_yyyy")
  private val sessionIds = linkedSetOf<String>()

  override fun <T> invokeRememberingLogs(action: () -> T): T = action()

  override fun save(languageName: String?, trainingPercentage: Int) {
    val logsDir = logsTemporaryStoragePath.toFile()
    if (!logsDir.exists()) return
    require(logsDir.isDirectory)
    val outputDir = if (languageName != null) finalStorageDir / languageName else finalStorageDir
    Files.createDirectories(outputDir)
    FileWriter(Paths.get(outputDir.toString(), "full.log").toString()).use { writer ->
      for (logChunk in (logsDir.listFiles() ?: emptyArray())
        .filter { it.name.startsWith("chunk") }
        .map { it.name.toString() }
        .sortedBy { it.substringAfterLast('_').substringBefore(".gz").toInt() }) {
        val chunkPath = logsTemporaryStoragePath / logChunk
        if (Files.exists(chunkPath)) {
          val log = GZIPInputStream(chunkPath.toFile().readBytes().inputStream()).reader().readText()
          sessionIds.addAll(log.split("\n").filter { it.isNotBlank() }.map { getSessionId(it) })
          writer.append(log)
          Files.delete(chunkPath)
        }
      }
    }
    saveLogs(outputDir.toString(), trainingPercentage)
    finalStorageDir.toFile().compress()
  }

  private fun saveLogs(outputDir: String, trainingPercentage: Int) {
    val fullLogsFile = Paths.get(outputDir, "full.log")
    if (!Files.exists(fullLogsFile)) return
    val trainSize = (sessionIds.size * trainingPercentage.toDouble() / 100.0).toInt()
    val trainSessionIds = sessionIds.take(trainSize).toSet()

    fullLogsFile.toFile().bufferedReader(bufferSize = 1024 * 1024).use {
      val firstLine = it.readLine() ?: return
      val userId = getUserId(firstLine)
      val trainingLogsWriter = getLogsWriter(outputDir, "train", userId)
      val validateLogsWriter = getLogsWriter(outputDir, "validate", userId)
      trainingLogsWriter.appendLine(firstLine)

      for (line in it.lines()) {
        val writer = if (trainSessionIds.contains(getSessionId(line))) trainingLogsWriter else validateLogsWriter
        writer.appendLine(line)
      }
      trainingLogsWriter.flush()
      validateLogsWriter.flush()
    }
    saveSessionsInfo(outputDir, sessionIds.size, trainSize)
    fullLogsFile.toFile().delete()
  }

  private fun getLogsWriter(outputDir: String, datasetType: String, userId: String): BufferedWriter {
    val resultDir = Paths.get(outputDir, datasetType, formatter.format(Date()))
    Files.createDirectories(resultDir)
    val logsFile = Paths.get(resultDir.toString(), userId)
    Files.createFile(logsFile)
    return logsFile.toFile().bufferedWriter()
  }

  private fun saveSessionsInfo(outputDir: String, all: Int, training: Int) {
    val infoFile = Paths.get(outputDir, "info")
    infoFile.toFile().writeText("All sessions: $all\nTraining sessions: $training\nValidate sessions: ${all - training}")
  }

  private fun getUserId(line: String) = line.split("\t")[3]
  private fun getSessionId(line: String) = line.split("\t")[4]
}