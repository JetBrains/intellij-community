// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.mocks

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.options.ObservableOptionEditor
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.*
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

  override fun getItems(params: SeParams): Flow<SeResultEvent> =
    delegate.getItems(params)

  override fun getFilterEditor(): ObservableOptionEditor<SeFilterState>? = null

  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean {
    println("Item selected: ${item.presentation.text}")
    return true
  }

  companion object {
    suspend fun create(project: Project,
                       sessionRef: DurableRef<SeSessionEntity>,
                       name: String,
                       providerIds: List<SeProviderId>,
                       forceRemote: Boolean = false): SeTabMock {
      val delegate = SeTabDelegate.create(project, sessionRef, name, providerIds, DataContext.EMPTY_CONTEXT, forceRemote)
      return SeTabMock(name, delegate)
    }
  }
}
