// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.api

import com.intellij.platform.searchEverywhere.SeParams
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
interface SeItemsProvider {
  val id: String

  fun interface Collector {
    suspend fun put(item: SeItem): Boolean
  }

  suspend fun collectItems(params: SeParams, collector: Collector)
  suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean
}
