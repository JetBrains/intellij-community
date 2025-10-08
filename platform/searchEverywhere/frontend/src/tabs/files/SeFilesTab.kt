// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.files

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfo
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfoProvider
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.frontend.tabs.target.SeTargetsFilterEditor
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.utils.initAsync
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeFilesTab(private val delegate: SeTabDelegate) : SeTab {
  override val name: String get() = NAME
  override val id: String get() = ID
  override val isIndexingDependent: Boolean get() = true

  private val filterEditor: SuspendLazyProperty<SeFilterEditor> = initAsync(delegate.scope) {
    SeTargetsFilterEditor(delegate.getSearchScopesInfos().firstOrNull(), delegate.getTypeVisibilityStates())
  }

  override fun getItems(params: SeParams): Flow<SeResultEvent> = delegate.getItems(params)

  override suspend fun getFilterEditor(): SeFilterEditor =
    filterEditor.getValue()

  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean {
    return delegate.itemSelected(item, modifiers, searchText)
  }

  override suspend fun getEmptyResultInfo(context: DataContext): SeEmptyResultInfo {
    return SeEmptyResultInfoProvider(getFilterEditor(),
                                     delegate.getProvidersIds(),
                                     delegate.canBeShownInFindResults()).getEmptyResultInfo(delegate.project, context)
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return delegate.canBeShownInFindResults()
  }

  override suspend fun openInFindToolWindow(session: SeSession, params: SeParams, initEvent: AnActionEvent): Boolean {
    return delegate.openInFindToolWindow(session, params, initEvent, false)
  }

  override suspend fun performExtendedAction(item: SeItemData): Boolean {
    return delegate.performExtendedAction(item)
  }

  override suspend fun isPreviewEnabled(): Boolean {
    return delegate.isPreviewEnabled()
  }

  override suspend fun getPreviewInfo(itemData: SeItemData): SePreviewInfo? {
    return delegate.getPreviewInfo(itemData, false)
  }

  override fun dispose() {
    Disposer.dispose(delegate)
  }

  companion object {
    @Internal
    const val ID: String = "FileSearchEverywhereContributor"
    @Internal
    val NAME: String = IdeBundle.message("search.everywhere.group.name.files")
  }
}
