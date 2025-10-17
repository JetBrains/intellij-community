// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.frontend.searchEverywhere

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfo
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfoProvider
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.frontend.tabs.target.SeTargetsFilterEditor
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.utils.suspendLazy
import com.intellij.vcs.git.SeGitProviderIdUtils
import git4idea.i18n.GitBundle
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeGitTab(private val delegate: SeTabDelegate) : SeTab {
  override val name: String get() = GitBundle.message("search.everywhere.group.name")
  override val id: String get() = ID
  private val filterEditor: SuspendLazyProperty<SeFilterEditor> = suspendLazy { SeTargetsFilterEditor(null, delegate.getTypeVisibilityStates(), false) }

  override fun getItems(params: SeParams): Flow<SeResultEvent> = delegate.getItems(params)

  override suspend fun getFilterEditor(): SeFilterEditor {
    return filterEditor.getValue()
  }

  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean {
    return delegate.itemSelected(item, modifiers, searchText)
  }

  override suspend fun getEmptyResultInfo(context: DataContext): SeEmptyResultInfo {
    return SeEmptyResultInfoProvider(getFilterEditor(),
                                     delegate.getProvidersIds(),
                                     delegate.canBeShownInFindResults()).getEmptyResultInfo(delegate.project, context)
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return false
  }

  override suspend fun performExtendedAction(item: SeItemData): Boolean {
    return delegate.performExtendedAction(item)
  }

  override suspend fun getPreviewInfo(itemData: SeItemData): SePreviewInfo? {
    return delegate.getPreviewInfo(itemData, false)
  }

  override suspend fun isPreviewEnabled(): Boolean {
    return delegate.isPreviewEnabled()
  }

  override suspend fun isExtendedInfoEnabled(): Boolean {
    return delegate.isExtendedInfoEnabled()
  }

  override suspend fun isCommandsSupported(): Boolean {
    return delegate.isCommandsSupported()
  }

  override fun dispose() {
    Disposer.dispose(delegate)
  }

  companion object {
    @ApiStatus.Internal
    const val ID: String = SeGitProviderIdUtils.GIT_OBJECTS_ID
  }
}
