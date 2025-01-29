// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers

import com.intellij.openapi.options.ObservableOptionEditor
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.api.SeFilterData
import com.intellij.platform.searchEverywhere.api.SeTab
import com.intellij.platform.searchEverywhere.frontend.SeTabHelper
import fleet.kernel.DurableRef
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeTabMock(override val name: String,
                private val helper: SeTabHelper): SeTab {
  override val shortName: String = name

  override fun getItems(params: SeParams): Flow<SeItemData> =
    helper.getItems(params)

  override fun getFilterEditor(): ObservableOptionEditor<SeFilterData>? = null

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
      val helper = SeTabHelper.create(project, sessionRef, providerIds, forceRemote)
      return SeTabMock(name, helper)
    }
  }
}
