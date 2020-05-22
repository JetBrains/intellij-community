// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object ClassToIntConverter {
  private val classToInt = ConcurrentHashMap<Class<*>, Int>()
  private val idGenerator = AtomicInteger()

  fun getInt(clazz: Class<*>): Int = classToInt.getOrPut(clazz) { idGenerator.getAndIncrement() }

  fun getClassSlowly(id: Int): Class<*>? {
    for (entry in classToInt) {
      if (entry.value == id) return entry.key
    }
    return null
  }

  fun getClassSlowlyOrDie(id: Int): Class<*> {
    for (entry in classToInt) {
      if (entry.value == id) return entry.key
    }
    error("Cannot find class")
  }
}
internal fun Class<*>.toInt(): Int = ClassToIntConverter.getInt(this)
internal inline fun <reified E> Int.toClass(): Class<E> = ClassToIntConverter.getClassSlowlyOrDie(this) as Class<E>

