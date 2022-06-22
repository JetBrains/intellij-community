// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.util.ReflectionUtil
import java.io.Closeable
import java.lang.reflect.Array
import java.lang.reflect.Field
import java.util.*
import java.util.function.Consumer

class ReflectionTraverser : Closeable {

  private val visited = IdentityHashMap<Any, Any?>()
  private val classCache = HashMap<Class<*>, ClassInfo>()
  fun walk(root: Any,
           classesToSkip: Collection<Class<*>> = emptyList(),
           classesToSkipChildren: Collection<Class<*>> = emptyList(),
           consumer: Consumer<Any>) {
    for (classToSkip in classesToSkip) {
      classCache[classToSkip] = skipClass
    }

    classCache[Object::class.java] = skipChildrenClass
    for (classToSkip in classesToSkipChildren) {
      classCache[classToSkip] = skipChildrenClass
    }
    val stack: Deque<Any> = LinkedList()
    stack.add(root)
    while (!stack.isEmpty()) {
      val current = stack.removeFirst() ?: continue
      if (visited.containsKey(current)) continue

      val clazz: Class<*> = current.javaClass
      val classInfo = getClassInfo(clazz)
      if (classInfo.skip) continue

      visited[current] = null
      consumer.accept(current)
      if (classInfo.skipChildren) continue

      if (clazz.isArray) {
        val len = Array.getLength(current)
        if (!clazz.componentType.isPrimitive) {
          val info = getClassInfo(clazz.componentType)
          if (!info.skip) {
            (0 until len).forEach { stack += (Array.get(current, it) ?: return@forEach) }
          }
        }
      }
      else when (current) {
        is Collection<*> -> walkCollection(stack, current)
        is Map<*, *> -> walkMap(stack, current)
        else -> walkFields(stack, current)
      }
    }
  }

  override fun close() {
    visited.clear()
    classCache.clear()
  }

  private fun walkFields(stack: Deque<Any>, current: Any) {
    val classInfo = getClassInfo(current.javaClass)
    for (field in classInfo.fields) {
      try {
        val value = field[current] ?: continue
        if (value.javaClass.isPrimitive) continue
        stack.add(value)
      }
      catch (ignored: IllegalAccessException) {
      }
    }
  }

  private fun getClassInfo(clazz: Class<*>) = classCache.computeIfAbsent(clazz) {
    val packageName = clazz.packageName
    if (packageName.startsWith("jdk.") ||
        packageName.startsWith("java.lang.module") ||
        packageName.startsWith("com.sun.proxy")) {
      return@computeIfAbsent skipClass
    }

    val fields = mutableListOf<Field>()
    try {
      val collectFields = ReflectionUtil.collectFields(clazz)
      for (field in collectFields) {
        if (!field.type.isPrimitive) {
          field.isAccessible = true
          fields += field
        }
      }
      return@computeIfAbsent ClassInfo(fields)
    }
    catch (t: Throwable) {
      return@computeIfAbsent skipClass
    }
  }

  private class ClassInfo(val fields: List<Field>) {
    val skip get() = this === skipClass
    val skipChildren get() = this === skipChildrenClass
  }

  companion object {
    private val skipClass = ClassInfo(emptyList())
    private val skipChildrenClass = ClassInfo(emptyList())

    @JvmStatic
    fun traverse(o: Any, consumer: Consumer<Any>) {
      ReflectionTraverser().use { it.walk(o, emptyList(), emptyList(), consumer) }
    }

    private fun walkCollection(stack: Deque<Any>, col: Collection<Any?>) {
      col.filterTo(stack) { it != null && !it.javaClass.isPrimitive }
    }

    private fun walkMap(stack: Deque<Any>, map: Map<*, *>) {
      val entrySet = try {
        map.entries
      }
      catch (e: UnsupportedOperationException) {
        null
      }

      if (entrySet != null) {
        for (entry in entrySet) {
          val key = entry.key ?: continue
          if (!key.javaClass.isPrimitive) {
            stack += key
          }
          val value = entry.value ?: continue
          if (value.javaClass.isPrimitive) continue
          stack += value
        }
      } else {
        walkCollection(stack, map.keys)
        walkCollection(stack, map.values)
      }
    }
  }
}