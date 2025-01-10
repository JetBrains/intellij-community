// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.platform.searchEverywhere.api.SeTabFilterData
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Serializable
sealed interface SeParams {
  val text: String
  val filterData: SeTabFilterData?
}

@ApiStatus.Experimental
@Serializable
data class SeTextSearchParams(override val text: String,
                              override val filterData: SeTabFilterData?) : SeParams

@ApiStatus.Internal
@Serializable
data class SeActionParams(override val text: String,
                          override val filterData: SeTabFilterData?,
                          val includeDisabled: Boolean): SeParams
