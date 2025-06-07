package com.intellij.cce.workspace.storages

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.cce.workspace.IndividualScoresSerializer
import com.intellij.cce.workspace.info.FileEvaluationDataInfo
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Paths


open class IndividualScoresStorage(storageDir: String) : StorageWithMetadataBase(storageDir) {
  companion object {
    private val gson = Gson()
    private val individualScoresSerializer = IndividualScoresSerializer()
  }

  private var evaluationDataFiles: MutableMap<String, String> = mutableMapOf()
  override val metadataFile: File = Paths.get(storageDir, "files_individual_scores_data.json").toFile()
  private val keyValueStorage = FileArchivesStorage(filesDir.toString())

  open fun saveIndividiualScores(metricsInfo: FileEvaluationDataInfo) {
    val json = individualScoresSerializer.serialize(metricsInfo)
    val archivePath = keyValueStorage.save("${toFileName(metricsInfo.filePath)}_individual_scores_data.json", json)
    evaluationDataFiles[metricsInfo.filePath] = archivePath
  }

  override fun saveMetadata() {
    val json = gson.toJson(evaluationDataFiles)
    metadataFile.writeText(json, StandardCharsets.UTF_8)
  }

  open fun getIndividualScoresFiles(): List<Pair<String, String>> {
    if (!metadataFile.exists()) return emptyList()
    val json = metadataFile.readText()
    val type = object : TypeToken<MutableMap<String, String>>() {}.type
    evaluationDataFiles = gson.fromJson(json, type)
    for (path in evaluationDataFiles.keys) evaluationDataFiles[path] = evaluationDataFiles[path]!!
    return evaluationDataFiles.entries.map { it.toPair() }
  }

  open fun getIndividualScores(path: String): FileEvaluationDataInfo {
    val metricsPath = evaluationDataFiles[path] ?: throw NoSuchElementException("Metrics file not found for path: $path")
    return individualScoresSerializer.deserialize(keyValueStorage.get(metricsPath))
  }
}
