// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.mocks

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.frontend.tabs.SeDefaultTabBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeTabMock(
  override val name: String,
  delegate: SeTabDelegate,
) : SeDefaultTabBase(delegate) {
  override val id: String = name
  override val priority: Int get() = 0

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
