// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
@Serializable
sealed class SeFilterState {
  fun get(key: String): List<String>? = (this as? Data)?.map?.get(key)
  fun getOne(key: String): String? = (this as? Data)?.map?.get(key)?.firstOrNull()
  fun getBoolean(key: String): Boolean? = (this as? Data)?.map?.get(key)?.firstOrNull()?.toBoolean()

  @Serializable
  object Empty : SeFilterState()

  @Serializable
  class Data(val map: Map<String, List<String>>) : SeFilterState()
}
