// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.mocks

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeTabMock(
  override val name: String,
  private val delegate: SeTabDelegate,
) : SeTab {
  override val id: String = name

  override fun getItems(params: SeParams): Flow<SeResultEvent> =
    delegate.getItems(params)

  override suspend fun getFilterEditor(): SeFilterEditor? = null

  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean {
    println("Item selected: ${item.presentation.text}")
    return true
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return false
  }

  override suspend fun performExtendedAction(item: SeItemData): Boolean {
    return false
  }

  override suspend fun getPreviewInfo(itemData: SeItemData): SePreviewInfo? {
    return delegate.getPreviewInfo(itemData, false)
  }

  override suspend fun isPreviewEnabled(): Boolean {
    return delegate.isPreviewEnabled()
  }

  override fun dispose() {
    Disposer.dispose(delegate)
  }

  companion object {
    fun create(
      project: Project?,
      session: SeSession,
      name: String,
      providerIds: List<SeProviderId>,
      initEvent: AnActionEvent,
      scope: CoroutineScope,
    ): SeTabMock {
      val delegate = SeTabDelegate(project, session, name, providerIds, initEvent, scope)
      return SeTabMock(name, delegate)
    }
  }
}
