package com.intellij.completion.ml.local.models

import com.intellij.completion.ml.local.models.api.LocalModel
import com.intellij.completion.ml.local.models.frequency.FrequencyLocalModel
import com.intellij.openapi.project.Project

class LocalModelsManager private constructor(private val project: Project) {
  companion object {
    fun getInstance(project: Project): LocalModelsManager = project.getService(LocalModelsManager::class.java)
  }
  private val models = mutableMapOf<String, LocalModel>()

  fun getModels(): List<LocalModel> = listOf(
    models.getOrPut("frequency") { FrequencyLocalModel.create(project) }
  )

  inline fun <reified T : LocalModel> getModel(): T? = getModels().filterIsInstance<T>().firstOrNull()
}