// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
@Serializable
sealed class SeFilterState {
  abstract fun get(key: String): List<String>?
  fun getOne(key: String): String? = get(key)?.firstOrNull()
  fun getBoolean(key: String): Boolean? = get(key)?.firstOrNull()?.toBoolean()

  @Serializable
  object Empty : SeFilterState() {
    override fun get(key: String): List<String>? = null
  }

  @Serializable
  class Data(private val map: Map<String, List<String>>) : SeFilterState() {
    override fun get(key: String): List<String>? = map[key]
  }
}
