// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
class RecorderOptionProvider(initialOptions: Map<String, String> = emptyMap()) {
  private val recorderOptions: MutableMap<String, String> = ConcurrentHashMap(initialOptions)
  private val recorderOptionsCache: MutableMap<String, Any> = ConcurrentHashMap()

  fun update(newOptions: Map<String, String>) {
    // clearing the cache at the end because we prefer here a previous value rather than null in case there is a race condition
    recorderOptions.clear()
    recorderOptions.putAll(newOptions)
    recorderOptionsCache.clear()
  }

  fun getStringOption(key: String): String? = getOption(key) { it }

  fun getIntOption(key: String): Int? = getOption(key) { it.toIntOrNull() }

  fun getListOption(key: String): List<String>? = getOption(key) {
    it.split(",").map { it.trim() }.filter { it.isNotEmpty() }
  }

  // calculate and cache
  private inline fun <reified T> getOption(key: String, transform: (String) -> T?): T? {
    val cachedValue = recorderOptionsCache[key]
    if (cachedValue != null) {
      return cachedValue as? T
    } else {
      val value = recorderOptions[key]
      if (value == null) {
        recorderOptionsCache.remove(key)
        return null
      } else {
        val transformed = transform(value)
        if (transformed != null) {
          recorderOptionsCache[key] = transformed
          return transformed
        } else {
          return null
        }
      }
    }
  }
}
