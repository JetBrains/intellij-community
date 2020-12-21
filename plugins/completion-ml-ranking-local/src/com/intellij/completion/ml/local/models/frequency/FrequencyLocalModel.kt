package com.intellij.completion.ml.local.models.frequency

import com.intellij.completion.ml.local.models.api.LocalModel
import com.intellij.completion.ml.local.models.api.LocalModelBuilder
import com.intellij.completion.ml.local.models.api.LocalModelFeaturesProvider
import com.intellij.completion.ml.local.models.storage.ClassesFrequencyStorage
import com.intellij.completion.ml.local.models.storage.MethodsFrequencyStorage
import com.intellij.completion.ml.local.util.LocalModelsUtil
import com.intellij.openapi.project.Project

class FrequencyLocalModel private constructor(methodsFrequencyStorage: MethodsFrequencyStorage,
                                              classesFrequencyStorage: ClassesFrequencyStorage) : LocalModel {
  companion object {
    fun create(project: Project): FrequencyLocalModel {
      val storagesPath = LocalModelsUtil.storagePath(project)
      val methodsFrequencyStorage = MethodsFrequencyStorage.getStorage(storagesPath)
      val classesFrequencyStorage = ClassesFrequencyStorage.getStorage(storagesPath)
      return FrequencyLocalModel(methodsFrequencyStorage, classesFrequencyStorage)
    }
  }

  override val builder: LocalModelBuilder = FrequencyLocalModelBuilder(methodsFrequencyStorage, classesFrequencyStorage)

  override val featuresProvider: LocalModelFeaturesProvider = FrequencyLocalModelFeaturesProvider(methodsFrequencyStorage,
                                                                                                  classesFrequencyStorage)
}