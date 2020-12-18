package com.intellij.completion.ml.local.models.frequency

class ClassFrequencies(private var frequency: Int = 0,
                       private var methodsFrequency: Int = 0,
                       private val methods: MutableMap<Int, Int> = mutableMapOf()) {
  companion object {
    fun withMethodUsage(methodName: String): ClassFrequencies {
      return ClassFrequencies(0, 1, mutableMapOf(methodName.hashCode() to 1))
    }

    fun withClassUsage(): ClassFrequencies {
      return ClassFrequencies(1, 0, mutableMapOf())
    }
  }

  fun addClassUsage() {
    frequency++
  }

  fun addMethodUsage(name: String) {
    methodsFrequency++
    val hash = name.hashCode()
    methods[hash] = methods.getOrDefault(hash, 0) + 1
  }

  fun merge(another: ClassFrequencies): ClassFrequencies {
    frequency += another.frequency
    methodsFrequency += another.methodsFrequency
    for (method in another.methods) {
      methods[method.key] = methods.getOrDefault(method.key, 0) + method.value
    }
    return this
  }

  fun getMethodFrequency(methodName: String): Int = methods[methodName.hashCode()] ?: 0

  fun getFrequency(): Int = frequency

  fun getMethodsFrequency(): Int = methodsFrequency

  fun getMethods(): Map<Int, Int> = methods
}