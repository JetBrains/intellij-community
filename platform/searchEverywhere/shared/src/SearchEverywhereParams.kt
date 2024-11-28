// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface SearchEverywhereParams {
  val text: String
}

@ApiStatus.Experimental
data class SearchEverywhereTextSearchParams(override val text: String) : SearchEverywhereParams

@ApiStatus.Internal
data class ActionSearchParams(override val text: String,
                              val includeDisabled: Boolean): SearchEverywhereParams
