package com.intellij.completion.ml.local.models.frequency

import com.intellij.completion.ml.local.models.storage.ClassesFrequencyStorage
import com.intellij.completion.ml.local.models.storage.MethodsFrequencyStorage

class UsagesTracker(private val methodsStorage: MethodsFrequencyStorage,
                    private val classesStorage: ClassesFrequencyStorage) {

  fun classUsed(name: String) {
    classesStorage.addClassUsage(name)
  }

  fun methodUsed(className: String, methodName: String) {
    methodsStorage.addMethodUsage(className, methodName)
  }
}