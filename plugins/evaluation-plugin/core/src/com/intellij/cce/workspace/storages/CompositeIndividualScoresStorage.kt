package com.intellij.cce.workspace.storages

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.cce.workspace.info.FileEvaluationDataInfo
import java.nio.file.Paths

class CompositeIndividualScoresStorage(storageDir: String) : IndividualScoresStorage(storageDir) {
  private val storages = mutableMapOf<String, IndividualScoresStorage>()

  override fun saveIndividiualScores(metricsInfo: FileEvaluationDataInfo) {
    val projectName = metricsInfo.projectName
    val storage = storages.getOrPut(projectName) { IndividualScoresStorage(Paths.get(storageDir, projectName).toString()) }
    storage.saveIndividiualScores(metricsInfo)
  }

  override fun saveMetadata() {
    storages.forEach { (_, storage) -> storage.saveMetadata() }
  }

  override fun getIndividualScoresFiles(): List<Pair<String, String>> {
    if (!metadataFile.exists()) return emptyList()
    val json = metadataFile.readText()
    val type = object : TypeToken<ArrayList<String>>() {}.type
    val storagesList: List<String> = gson.fromJson(json, type)
    storagesList.forEach { storages[it] = storages[it] ?: IndividualScoresStorage(Paths.get(storageDir, it).toString()) }

    val result = mutableListOf<Pair<String, String>>()
    for ((projectName, storage) in storages) {
      result.addAll(storage.getIndividualScoresFiles().map { "$projectName:${it.first}" to it.second })
    }
    return result
  }

  override fun getIndividualScores(path: String): FileEvaluationDataInfo {
    val (projectName, filePath) = path.split(":", limit = 2)
    val storage = storages[projectName]
                  ?: throw NoSuchElementException("IndividualScoresStorage not found for project: $projectName")
    return storage.getIndividualScores(filePath)
  }

  companion object {
    private val gson = Gson()
  }
}
