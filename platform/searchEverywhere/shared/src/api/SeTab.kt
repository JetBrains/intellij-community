// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.api

import com.intellij.openapi.options.ObservableOptionEditor
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
interface SeTab {
  val name: String
  val shortName: String

  fun getItems(params: SeParams): Flow<SeItemData>

  fun getFilterEditor(): ObservableOptionEditor<SeFilterData>?

  suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean
}