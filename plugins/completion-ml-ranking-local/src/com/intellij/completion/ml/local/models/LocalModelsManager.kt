package com.intellij.completion.ml.local.models

import com.intellij.completion.ml.local.models.api.LocalModel
import com.intellij.completion.ml.local.models.frequency.ClassesFrequencyLocalModel
import com.intellij.completion.ml.local.models.frequency.MethodsFrequencyLocalModel
import com.intellij.openapi.project.Project

class LocalModelsManager private constructor(private val project: Project) {
  companion object {
    fun getInstance(project: Project): LocalModelsManager = project.getService(LocalModelsManager::class.java)
  }
  private val models = mutableMapOf<String, LocalModel>()

  fun getModels(): List<LocalModel> = listOf(
    models.getOrPut("methods_frequency") { MethodsFrequencyLocalModel.create(project) },
    models.getOrPut("classes_frequency") { ClassesFrequencyLocalModel.create(project) }
  )

  inline fun <reified T : LocalModel> getModel(): T? = getModels().filterIsInstance<T>().firstOrNull()
}