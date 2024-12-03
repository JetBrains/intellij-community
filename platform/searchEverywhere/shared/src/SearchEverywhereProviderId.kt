// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class SearchEverywhereProviderId(val value: String) {
  companion object {
    fun of(provider: SearchEverywhereItemsProvider): SearchEverywhereProviderId = SearchEverywhereProviderId(provider.javaClass.name)
  }
}