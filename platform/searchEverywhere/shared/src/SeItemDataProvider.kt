// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.openapi.Disposable
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface SeItemDataProvider: Disposable {
  val id: SeProviderId

  fun getItems(params: SeParams): Flow<SeItemData>

  suspend fun itemSelected(itemData: SeItemData,
                           modifiers: Int,
                           searchText: String): Boolean

  suspend fun getSearchScopesInfo(): SeSearchScopesInfo?
}