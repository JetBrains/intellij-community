package com.intellij.completion.ml.local.models.frequency

import com.intellij.completion.ml.local.models.api.LocalModel
import com.intellij.completion.ml.local.models.storage.ClassesFrequencyStorage
import com.intellij.completion.ml.local.models.storage.MethodsFrequencyStorage
import com.intellij.completion.ml.local.util.LocalModelsUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor

class FrequencyLocalModel private constructor(private val methodsFrequencyStorage: MethodsFrequencyStorage,
                                              private val classesFrequencyStorage: ClassesFrequencyStorage) : LocalModel {
  companion object {
    fun create(project: Project): FrequencyLocalModel {
      val storagesPath = LocalModelsUtil.storagePath(project)
      val methodsFrequencyStorage = MethodsFrequencyStorage.getStorage(storagesPath)
      val classesFrequencyStorage = ClassesFrequencyStorage.getStorage(storagesPath)
      return FrequencyLocalModel(methodsFrequencyStorage, classesFrequencyStorage)
    }
  }

  private val storages = listOf(methodsFrequencyStorage, classesFrequencyStorage)

  fun totalMethodsCount(): Int = methodsFrequencyStorage.totalMethods

  fun totalMethodsUsages(): Int = methodsFrequencyStorage.totalMethodsUsages

  fun totalClassesCount(): Int = classesFrequencyStorage.totalClasses

  fun totalClassesUsages(): Int = classesFrequencyStorage.totalClassesUsages

  fun getMethodsByClass(className: String): MethodsFrequencies? = methodsFrequencyStorage.get(className)

  fun getClass(className: String): Int? = classesFrequencyStorage.get(className)

  override fun fileVisitor(): PsiElementVisitor = ReferenceFrequencyVisitor(UsagesTracker(methodsFrequencyStorage, classesFrequencyStorage))

  override fun onStarted() {
    storages.forEach { it.setValid(false) }
  }

  override fun onFinished() {
    storages.forEach { it.setValid(true) }
  }
}