// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
@ApiStatus.Internal
@Serializable
sealed class SeFilterState {
  @Serializable
  data object Empty : SeFilterState()
  @Serializable
  data class Data(val map: Map<String, SeFilterValue>) : SeFilterState()
}

@Experimental
@ApiStatus.Internal
@Serializable
sealed class SeFilterValue {
  @Serializable
  data class One(val value: String) : SeFilterValue()
  @Serializable
  data class Many(val values: List<String>) : SeFilterValue()
}
