package com.intellij.completion.ml.local.models.frequency

class UsagesTracker(private val storage: FrequencyStorage) {
  fun classUsed(name: String) {
    storage.addClassUsage(name)
  }

  fun methodUsed(className: String, methodName: String) {
    storage.addMethodUsage(className, methodName)
  }
}