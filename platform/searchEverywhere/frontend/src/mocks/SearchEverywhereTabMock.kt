// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.mocks

import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.frontend.SearchEverywhereTabHelper
import fleet.kernel.DurableRef
import kotlinx.coroutines.flow.Flow

class SearchEverywhereTabMock(override val name: String,
                              private val helper: SearchEverywhereTabHelper): SearchEverywhereTab {
  override val shortName: String = name

  override fun getItems(params: SearchEverywhereParams): Flow<SearchEverywhereItemData> =
    helper.getItems(params)

  companion object {
    suspend fun create(project: Project,
                       sessionRef: DurableRef<SearchEverywhereSessionEntity>,
                       name: String,
                       providerIds: List<SearchEverywhereProviderId>,
                       forceRemote: Boolean = false): SearchEverywhereTabMock {
      val helper = SearchEverywhereTabHelper.create(project, sessionRef, providerIds, forceRemote)
      return SearchEverywhereTabMock(name, helper)
    }
  }
}
