// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.mocks

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import fleet.kernel.DurableRef
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeTabMock(override val name: String,
                private val delegate: SeTabDelegate
): SeTab {
  override val shortName: String = name
  override val id: String = name

  override fun getItems(params: SeParams): Flow<SeResultEvent> =
    delegate.getItems(params)

  override suspend fun getFilterEditor(): SeFilterEditor? = null

  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean {
    println("Item selected: ${item.presentation.text}")
    return true
  }

  override fun dispose() {
    Disposer.dispose(delegate)
  }

  companion object {
    fun create(
      project: Project?,
      sessionRef: DurableRef<SeSessionEntity>,
      name: String,
      providerIds: List<SeProviderId>
    ): SeTabMock {
      val delegate = SeTabDelegate(project, sessionRef, name, providerIds, DataContext.EMPTY_CONTEXT)
      return SeTabMock(name, delegate)
    }
  }
}
