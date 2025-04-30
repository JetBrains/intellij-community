// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.text

import com.intellij.find.FindBundle
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTextTab(private val delegate: SeTabDelegate) : SeTab {
  override val name: String get() = FindBundle.message("search.everywhere.group.name")
  override val shortName: String get() = name
  override val id: String get() = "TextSearchContributor"

  override fun getItems(params: SeParams): Flow<SeResultEvent> =
    delegate.getItems(params)

  override suspend fun getFilterEditor(): SeFilterEditor? = null

  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean {
    return delegate.itemSelected(item, modifiers, searchText)
  }

  override fun dispose() {
    Disposer.dispose(delegate)
  }
}