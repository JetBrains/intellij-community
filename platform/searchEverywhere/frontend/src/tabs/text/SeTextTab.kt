// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.text

import com.intellij.find.FindBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfo
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import fleet.kernel.DurableRef
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.utils.initAsync
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTextTab(private val delegate: SeTabDelegate) : SeTab {
  override val name: String get() = FindBundle.message("search.everywhere.group.name")
  override val shortName: String get() = name
  override val id: String get() = ID
  private val filterEditorDisposable = Disposer.newDisposable()
  private val filterEditor: SuspendLazyProperty<SeTextFilterEditor> = initAsync(delegate.scope) {
    SeTextFilterEditor(delegate.project, delegate.getSearchScopesInfos().firstOrNull(), filterEditorDisposable)
  }

  override fun getItems(params: SeParams): Flow<SeResultEvent> = delegate.getItems(params)

  override suspend fun getFilterEditor(): SeFilterEditor = filterEditor.getValue()

  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean {
    return delegate.itemSelected(item, modifiers, searchText)
  }

  override suspend fun getEmptyResultInfo(context: DataContext): SeEmptyResultInfo {
    return SeTextTabEmptyResultInfoProvider(filterEditor.getValue(), delegate.project).getEmptyResultInfo()
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return delegate.canBeShownInFindResults()
  }

  override suspend fun openInFindToolWindow(sessionRef: DurableRef<SeSessionEntity>, params: SeParams, initEvent: AnActionEvent): Boolean {
    return delegate.openInFindToolWindow(sessionRef, params, initEvent, false)
  }

  override fun dispose() {
    Disposer.dispose(filterEditorDisposable)
    Disposer.dispose(delegate)
  }

  companion object {
    @ApiStatus.Internal
    const val ID: String = "TextSearchContributor"
  }
}