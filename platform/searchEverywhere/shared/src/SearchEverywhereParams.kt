// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface SearchEverywhereParams {
  val text: String
  val session: SearchEverywhereSession
}

@ApiStatus.Internal
data class SearchEverywhereTextSearchParams(override val text: String,
                                            override val session: SearchEverywhereSession) : SearchEverywhereParams

@ApiStatus.Internal
data class ActionSearchParams(override val text: String,
                              override val session: SearchEverywhereSession,
                              val includeDisabled: Boolean): SearchEverywhereParams
