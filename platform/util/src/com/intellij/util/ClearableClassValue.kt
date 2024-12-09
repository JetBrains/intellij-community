// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus

/**
 * This ClassValue is made to workaround a memory leak (see IJPL-171223) when underlying ClassValueMap.cacheArray holds references
 * to classes even the classes' classloader was unloaded. This prevents full unload of these classes.
 */
@ApiStatus.Internal
abstract class ClearableClassValue<T> : ClassValue<T>() {
  private val typeCache = ContainerUtil.createWeakSet<Class<*>>()

  fun clear() {
    for (clazz in typeCache) {
      remove(clazz)
    }
    typeCache.clear()
  }

  protected override fun computeValue(aClass: Class<*>): T? {
    typeCache.add(aClass)
    return computeValueImpl(aClass)
  }

  abstract fun computeValueImpl(aClass: Class<*>): T?
}