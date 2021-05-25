// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.util.ReflectionUtil
import java.lang.reflect.Array
import java.lang.reflect.Field
import java.util.*

class ReflectionTraverser {
  interface Visitor {
    fun process(instance: Any)
  }

  private val visited = IdentityHashMap<Any, Any?>()
  private val classCache = HashMap<Class<*>, ClassInfo>()
  fun walk(root: Any, visitor: Visitor) {
    val stack: Deque<Any> = LinkedList()
    stack.add(root)
    while (!stack.isEmpty()) {
      val current = stack.removeFirst() ?: continue
      if (visited.containsKey(current)) continue

      val clazz: Class<*> = current.javaClass
      val classInfo = getClassInfo(clazz)
      if (classInfo.skip) continue

      visited[current] = null
      visitor.process(current)
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

  private fun getClassInfo(current: Class<*>): ClassInfo = classCache.computeIfAbsent(current) { ClassInfo(current) }

  class ClassInfo(c: Class<*>) {
    var skip = false
    val fields = mutableListOf<Field>()

    init {
      try {
        val packageName = c.packageName
        if (packageName.startsWith("jdk.") ||
            packageName.startsWith("java.lang.module") ||
            packageName.startsWith("com.sun.proxy")) {
          skip = true
        }
        else {
          val collectFields = ReflectionUtil.collectFields(c)
          for (it in collectFields) {
            if (!it.type.isPrimitive) {
              it.isAccessible = true
              fields += it
            }
          }
        }
      }
      catch (t: Throwable) {
        skip = true
      }
    }
  }

  companion object {
    @JvmStatic
    fun traverse(o: Any, visitor: Visitor) {
      val traverse = ReflectionTraverser()
      traverse.walk(o, visitor)
      traverse.visited.clear()
      traverse.classCache.clear()
    }

    private fun walkCollection(stack: Deque<Any>, col: Collection<Any?>) {
      col.filterTo(stack) { it != null && !it.javaClass.isPrimitive }
    }

    private fun walkMap(stack: Deque<Any>, map: Map<*, *>) {
      for (entry in map) {
        val key = entry.key ?: continue
        if (!key.javaClass.isPrimitive) {
          stack += key
        }
        val value = entry.value ?: continue
        if (value.javaClass.isPrimitive) continue
        stack += value
      }
    }
  }
}