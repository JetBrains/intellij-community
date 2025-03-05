// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.platform.searchEverywhere.api.SeFilterData
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
@Serializable
sealed interface SeParams {
  val text: String
  val filterData: SeFilterData?
}

@ApiStatus.Experimental
@ApiStatus.Internal
@Serializable
data class SeTextSearchParams(override val text: String,
                              override val filterData: SeFilterData?) : SeParams

@ApiStatus.Internal
@Serializable
data class SeActionParams(override val text: String,
                          override val filterData: SeFilterData?,
                          val includeDisabled: Boolean): SeParams
