package com.intellij.completion.ml.local.models.frequency

import com.intellij.completion.ml.local.models.api.LocalModelBuilder
import com.intellij.completion.ml.local.models.storage.ClassesFrequencyStorage
import com.intellij.completion.ml.local.models.storage.MethodsFrequencyStorage
import com.intellij.psi.PsiElementVisitor

class FrequencyLocalModelBuilder(private val methodsFrequencyStorage: MethodsFrequencyStorage,
                                 private val classesFrequencyStorage: ClassesFrequencyStorage) : LocalModelBuilder {
  private val storages = listOf(methodsFrequencyStorage, classesFrequencyStorage)

  override fun visitor(): PsiElementVisitor = ReferenceFrequencyVisitor(UsagesTracker(methodsFrequencyStorage, classesFrequencyStorage))

  override fun onStarted() {
    storages.forEach { it.setValid(false) }
  }

  override fun onFinished() {
    storages.forEach { it.setValid(true) }
  }
}