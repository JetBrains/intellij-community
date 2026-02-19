// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.files

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfo
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfoProvider
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.frontend.tabs.SeDefaultTabBase
import com.intellij.platform.searchEverywhere.frontend.tabs.target.SeTargetsFilterEditor
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.utils.initAsync
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeFilesTab(delegate: SeTabDelegate) : SeDefaultTabBase(delegate) {
  override val name: String get() = NAME
  override val id: String get() = ID
  override val isIndexingDependent: Boolean get() = true
  override val priority: Int get() = PRIORITY

  private val filterEditor: SuspendLazyProperty<SeFilterEditor> = initAsync(delegate.scope) {
    SeTargetsFilterEditor(delegate.getSearchScopesInfos().firstOrNull(), delegate.getTypeVisibilityStates(), true)
  }

  override suspend fun getFilterEditor(): SeFilterEditor =
    filterEditor.getValue()

  override suspend fun getEmptyResultInfo(context: DataContext): SeEmptyResultInfo {
    return SeEmptyResultInfoProvider(getFilterEditor(),
                                     delegate.getProvidersIds(),
                                     delegate.canBeShownInFindResults()).getEmptyResultInfo(delegate.project, context)
  }

  override suspend fun canBeShownInFindResults(): Boolean = true

  companion object {
    @Internal
    const val ID: String = "FileSearchEverywhereContributor"

    @Internal
    val NAME: String = IdeBundle.message("search.everywhere.group.name.files")

    @ApiStatus.Internal
    const val PRIORITY: Int = 900
  }
}
