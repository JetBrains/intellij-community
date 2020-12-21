package com.intellij.completion.ml.local.models

import com.intellij.completion.ml.local.models.api.LocalModel
import com.intellij.completion.ml.local.models.api.LocalModelBuilder
import com.intellij.completion.ml.local.models.api.LocalModelFeaturesProvider
import com.intellij.completion.ml.local.models.frequency.FrequencyLocalModel
import com.intellij.openapi.project.Project

class LocalModelsManager private constructor(private val project: Project) {
  companion object {
    fun getInstance(project: Project): LocalModelsManager = project.getService(LocalModelsManager::class.java)
  }
  private val models = mutableMapOf<String, LocalModel>()

  private fun getModels(): List<LocalModel> = listOf(
    models.getOrPut("frequency") { FrequencyLocalModel.create(project) }
  )

  fun modelBuilders(): List<LocalModelBuilder> = getModels().map { it.builder }

  fun modelFeatureProviders(): List<LocalModelFeaturesProvider> = getModels().map { it.featuresProvider }
}