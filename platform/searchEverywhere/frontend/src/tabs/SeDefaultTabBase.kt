// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

/**
 * Default implementation of [SeTab] that delegates most of the API-specific calls to [SeTabDelegate]
 */
@ApiStatus.Experimental
abstract class SeDefaultTabBase(
  protected val delegate: SeTabDelegate
): SeTab {
  override fun getItems(params: SeParams): Flow<SeResultEvent> = delegate.getItems(params)
  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean = delegate.itemSelected(item, modifiers, searchText)
  override suspend fun canBeShownInFindResults(): Boolean = delegate.canBeShownInFindResults()

  override suspend fun openInFindToolWindow(session: SeSession, params: SeParams, initEvent: AnActionEvent): Boolean =
    if (canBeShownInFindResults()) delegate.openInFindToolWindow(session, params, initEvent, false) else false

  override suspend fun performExtendedAction(item: SeItemData): Boolean = delegate.performExtendedAction(item)
  override suspend fun isPreviewEnabled(): Boolean = delegate.isPreviewEnabled()
  override suspend fun getPreviewInfo(itemData: SeItemData): SePreviewInfo? = delegate.getPreviewInfo(itemData, false)
  override suspend fun isExtendedInfoEnabled(): Boolean = delegate.isExtendedInfoEnabled()
  override suspend fun isCommandsSupported(): Boolean = delegate.isCommandsSupported()

  override fun dispose() {
    Disposer.dispose(delegate)
  }
}