// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.files

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfo
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfoProvider
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.frontend.tabs.target.SeTargetsFilterEditor
import com.intellij.platform.searchEverywhere.frontend.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.frontend.utils.suspendLazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeFilesTab(private val delegate: SeTabDelegate): SeTab {
  override val name: String get() = IdeBundle.message("search.everywhere.group.name.files")
  override val shortName: String get() = name
  override val id: String get() = "FileSearchEverywhereContributor"
  private val filterEditor : SuspendLazyProperty<SeFilterEditor> = suspendLazy { SeTargetsFilterEditor (delegate.getSearchScopesInfos().firstOrNull(), delegate.getTypeVisibilityStates()) }

  override fun getItems(params: SeParams): Flow<SeResultEvent> =
    if (params.inputQuery.isEmpty()) emptyFlow()
    else delegate.getItems(params)

  override suspend fun getFilterEditor(): SeFilterEditor? =
    filterEditor.getValue()

  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean {
    return delegate.itemSelected(item, modifiers, searchText)
  }

  override suspend fun getEmptyResultInfo(context: DataContext): SeEmptyResultInfo? {
    return SeEmptyResultInfoProvider(getFilterEditor(),
                                     delegate.getProvidersIds(),
                                     delegate.canBeShownInFindResults()).getEmptyResultInfo(delegate.project, context)
  }

  override fun dispose() {
    Disposer.dispose(delegate)
  }
}
