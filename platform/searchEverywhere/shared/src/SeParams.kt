// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Represents search parameters for Search Everywhere requests.
 *
 * @property inputQuery The input string for the search.
 * @property filter The filter state applied to refine or customize the search results.
 */
@ApiStatus.Experimental
@Serializable
class SeParams(
  val inputQuery: String,
  val filter: SeFilterState,
)
