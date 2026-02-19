// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.text

import com.intellij.find.FindBundle
import com.intellij.find.FindManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeTextSearchOptions
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfo
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.frontend.tabs.SeDefaultTabBase
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.utils.initAsync
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTextTab(delegate: SeTabDelegate, registerShortcut: (AnAction) -> Unit) : SeDefaultTabBase(delegate) {
  override val name: String get() = NAME
  override val id: String get() = ID
  override val priority: Int get() = PRIORITY
  private val filterEditorDisposable = Disposer.newDisposable()
  private val filterEditor: SuspendLazyProperty<SeTextFilterEditor> = initAsync(delegate.scope) {
    SeTextFilterEditor(delegate.project, delegate.getSearchScopesInfos().firstOrNull(),
                       getTextSearchOptions(), filterEditorDisposable, registerShortcut)
  }

  override suspend fun getFilterEditor(): SeFilterEditor = filterEditor.getValue()

  override suspend fun getEmptyResultInfo(context: DataContext): SeEmptyResultInfo {
    return SeTextTabEmptyResultInfoProvider(filterEditor.getValue(), delegate.project).getEmptyResultInfo()
  }

  override suspend fun canBeShownInFindResults(): Boolean = true

  private fun getTextSearchOptions(): SeTextSearchOptions? {
    val project = delegate.project
    if (project == null) return null

    val findModel = FindManager.getInstance(project).findInProjectModel
    return SeTextSearchOptions(findModel.isCaseSensitive, findModel.isWholeWordsOnly, findModel.isRegularExpressions)
  }

  override fun dispose() {
    Disposer.dispose(filterEditorDisposable)
    super.dispose()
  }

  companion object {
    @ApiStatus.Internal
    const val ID: String = "TextSearchContributor"
    @ApiStatus.Internal
    val NAME: String = FindBundle.message("search.everywhere.group.name")
    @ApiStatus.Internal
    const val PRIORITY: Int = 250
  }
}