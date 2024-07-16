// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.gradle.toolingExtension.impl.util.GradleTreeTraverserUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ReflectionUtil
import org.apache.commons.lang3.ClassUtils
import java.lang.reflect.Array
import java.lang.reflect.Field
import java.util.*
import java.util.function.Consumer

class GradleObjectTraverser(
  private val classesToSkip: Set<Class<*>> = emptySet(),
  private val classesToSkipChildren: Set<Class<*>> = emptySet()
) {

  private val classInfoCache = HashMap<Class<*>, ClassInfo>()
  private val visitedObjects = IdentityHashMap<Any, Boolean>()

  fun walk(root: Any, consumer: Consumer<Any>) {
    GradleTreeTraverserUtil.depthFirstTraverseTreeWithPath(root) { path, anObject ->
      when {
        !isShouldBeTraversed(anObject) -> emptyList()
        else -> {
          visitedObjects[anObject] = true
          consumer.accept(anObject)
          getChildElementsForTraversing(path, anObject)
        }
      }
    }
  }

  private fun isShouldBeTraversed(anObject: Any): Boolean {
    return when {
      visitedObjects.containsKey(anObject) -> false
      isShouldBeTraversed(anObject.javaClass) -> true
      else -> false
    }
  }

  private fun isShouldBeTraversed(aClass: Class<*>): Boolean {
    return when {
      classesToSkip.contains(aClass) -> false
      aClass.isPrimitive -> false
      ClassUtils.isPrimitiveOrWrapper(aClass) -> false
      else -> true
    }
  }

  private fun getChildElementsForTraversing(traverserPath: List<Any>, anObject: Any): Collection<Any> {
    val aClass = anObject.javaClass
    return when {
      classesToSkipChildren.contains(aClass) -> emptyList()
      aClass.isArray -> getArrayElementsForTraversing(traverserPath, anObject)
      anObject is Collection<*> -> getCollectionElementsForTraversing(traverserPath, anObject)
      anObject is Map<*, *> -> getMapElementsForTraversing(traverserPath, anObject)
      else -> getObjectElementsForTraversing(traverserPath, anObject)
    }
  }

  private fun getArrayElementsForTraversing(traverserPath: List<Any>, anArray: Any): Collection<Any> {
    if (anArray.javaClass.componentType.isPrimitive) {
      return emptyList()
    }
    try {
      val result = ArrayList<Any>()
      val length = Array.getLength(anArray)
      for (index in 0 until length) {
        val element = Array.get(anArray, index)
        if (element != null && isShouldBeTraversed(element)) {
          result.add(element)
        }
      }
      return result
    }
    catch (exception: Throwable) {
      logTraverseError("Cannot traverse array elements", traverserPath, exception)
      return emptyList()
    }
  }

  private fun getCollectionElementsForTraversing(traverserPath: List<Any>, aCollection: Collection<*>): Collection<Any> {
    try {
      return aCollection.asSequence()
        .filterNotNull()
        .filter { isShouldBeTraversed(it) }
        .toList()
    }
    catch (exception: Throwable) {
      logTraverseError("Cannot traverse collection elements", traverserPath, exception)
      return emptyList()
    }
  }

  private fun getMapElementsForTraversing(traverserPath: List<Any>, map: Map<*, *>): Collection<Any> {
    val result = ArrayList<Any>()
    runCatching { map.entries }
      .onFailure { logTraverseError("Cannot traverse map entries", traverserPath, it) }
      .onSuccess { result.addAll(getMapEntryElementsForTraversing(traverserPath, it)) }
      .recover {
        runCatching { map.keys }
          .onFailure { logTraverseError("Cannot traverse map keys", traverserPath, it) }
          .onSuccess { result.addAll(getCollectionElementsForTraversing(traverserPath, it)) }
        runCatching { map.values }
          .onFailure { logTraverseError("Cannot traverse map values", traverserPath, it) }
          .onSuccess { result.addAll(getCollectionElementsForTraversing(traverserPath, it)) }
      }
    return result
  }

  private fun getMapEntryElementsForTraversing(traverserPath: List<Any>, entries: Set<Map.Entry<*, *>>): Collection<Any> {
    try {
      val result = ArrayList<Any>()
      for (entry in entries) {
        val key = entry.key
        if (key != null && isShouldBeTraversed(key)) {
          result.add(key)
        }
        val value = entry.value
        if (value != null && isShouldBeTraversed(value)) {
          result.add(value)
        }
      }
      return result
    }
    catch (exception: Throwable) {
      logTraverseError("Cannot traverse collection elements", traverserPath, exception)
      return emptyList()
    }
  }

  private fun getObjectElementsForTraversing(traverserPath: List<Any>, anObject: Any): Collection<Any> {
    val result = ArrayList<Any>()
    val classInfo = getCachedClassInfo(anObject.javaClass)
    for (field in classInfo.fields) {
      try {
        val value = field[anObject]
        if (value != null && isShouldBeTraversed(value)) {
          result.add(value)
        }
      }
      catch (ignored: IllegalAccessException) {
      }
      catch (exception: Throwable) {
        logTraverseError("Cannot traverse object fields", traverserPath, exception)
      }
    }
    return result
  }

  private fun getCachedClassInfo(aClass: Class<*>): ClassInfo {
    return classInfoCache.computeIfAbsent(aClass) {
      createClassInfo(aClass) ?: ClassInfo.EMPTY
    }
  }

  private fun createClassInfo(aClass: Class<*>): ClassInfo? {
    val packageName = aClass.packageName
    if (packageName.startsWith("jdk.") ||
        packageName.startsWith("java.lang.module") ||
        packageName.startsWith("com.sun.proxy")) {
      return null
    }

    try {
      val fieldsForTraversing = mutableListOf<Field>()
      val fields = ReflectionUtil.collectFields(aClass)
      for (field in fields) {
        if (isShouldBeTraversed(field.type)) {
          field.isAccessible = true
          fieldsForTraversing += field
        }
      }
      return ClassInfo(fieldsForTraversing)
    }
    catch (ignored: Throwable) {
      return null
    }
  }

  private class ClassInfo(val fields: List<Field>) {
    companion object {
      val EMPTY = ClassInfo(emptyList())
    }
  }

  companion object {

    private val LOG = logger<GradleObjectTraverser>()

    private fun logTraverseError(message: String, traverserPath: List<Any>, exception: Throwable) {
      val rawTraverserPath = traverserPath.joinToString("\n") { " - $it" }
      LOG.error("$message\nObject path:\n$rawTraverserPath", exception)
    }
  }
}