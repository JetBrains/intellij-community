package com.intellij.completion.ml.local.models.frequency

import com.intellij.util.Processor
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.PersistentHashMap
import com.intellij.util.io.createDirectories
import java.io.DataInput
import java.io.DataOutput
import java.nio.file.Path

class FrequencyStorage(storageDirectory: Path) {
  companion object {
    private const val STORAGE_NAME = "frequency"
    private const val CLASS_FREQUENCY_THRESHOLD = 5
  }

  init {
    storageDirectory.createDirectories()
  }

  private val classFrequencies = mutableMapOf<String, Int>()
  private val storage = PersistentHashMap(storageDirectory.resolve(STORAGE_NAME),
                                          EnumeratorStringDescriptor(),
                                          MyDataExternalizer())

  @Volatile var totalMethods = 0
    private set

  @Volatile var totalMethodsUsages = 0
    private set

  @Volatile var totalClasses = 0
    private set

  @Volatile var totalClassesUsages = 0
    private set

  @Synchronized
  fun addMethodUsage(className: String, methodName: String) {
    val existingFrequencies = storage.get(className)
    if (existingFrequencies == null) {
      storage.put(className, ClassFrequencies.withMethodUsage(methodName))
    } else {
      existingFrequencies.addMethodUsage(methodName)
      storage.put(className, existingFrequencies)
    }
  }

  @Synchronized
  fun addClassUsage(className: String) {
    val existingFrequencies = storage.get(className)
    if (existingFrequencies == null) {
      storage.put(className, ClassFrequencies.withClassUsage())
    } else {
      existingFrequencies.addClassUsage()
      storage.put(className, existingFrequencies)
    }
  }

  fun get(className: String): ClassFrequencies? = storage.get(className)

  fun getClassFrequency(className: String): Int? = classFrequencies[className]

  fun clear() {
    classFrequencies.clear()
    storage.closeAndClean()
  }

  fun postProcess() {
    storage.processKeys(Processor {
      val frequencies = storage.get(it) ?: return@Processor true
      val classFrequency = frequencies.getFrequency()
      totalClasses++
      totalClassesUsages += classFrequency
      totalMethods += frequencies.getMethods().size
      totalMethodsUsages += frequencies.getMethodsFrequency()
      if (classFrequency >= CLASS_FREQUENCY_THRESHOLD) {
        classFrequencies[it] = classFrequency
      }
      return@Processor true
    })
    storage.force()
  }

  private class MyDataExternalizer : DataExternalizer<ClassFrequencies> {
    override fun save(out: DataOutput, value: ClassFrequencies) {
      out.writeInt(value.getFrequency())
      out.writeInt(value.getMethodsFrequency())
      val methods = value.getMethods()
      out.writeInt(methods.size)
      for (method in methods) {
        out.writeInt(method.key)
        out.writeInt(method.value)
      }
    }

    override fun read(input: DataInput): ClassFrequencies {
      val frequency = input.readInt()
      val methodsFrequency = input.readInt()
      val count = input.readInt()
      val methods = mutableMapOf<Int, Int>()
      for (i in 1..count) {
        val index = input.readInt()
        methods[index] = input.readInt()
      }
      return ClassFrequencies(frequency, methodsFrequency, methods)
    }
  }
}