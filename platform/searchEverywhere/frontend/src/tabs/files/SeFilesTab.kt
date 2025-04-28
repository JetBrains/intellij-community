// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.files

import com.intellij.ide.IdeBundle
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.SeSearchScopesInfo
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.frontend.tabs.target.SeTargetsFilterEditor
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeFilesTab(private val delegate: SeTabDelegate,
                 private val scopeInfo: SeSearchScopesInfo?,
                 private val typeVisibilityStates: List<SeTypeVisibilityStatePresentation>?): SeTab {
  override val name: String get() = IdeBundle.message("search.everywhere.group.name.files")
  override val shortName: String get() = name
  override val id: String get() = "FileSearchEverywhereContributor"

  override fun getItems(params: SeParams): Flow<SeResultEvent> =
    delegate.getItems(params)

  override fun getFilterEditor(): SeFilterEditor? = SeTargetsFilterEditor(scopeInfo, typeVisibilityStates)

  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean {
    return delegate.itemSelected(item, modifiers, searchText)
  }

  override fun dispose() {
    Disposer.dispose(delegate)
  }
}
