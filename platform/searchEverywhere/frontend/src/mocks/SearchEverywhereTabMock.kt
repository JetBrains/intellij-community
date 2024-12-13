// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.mocks

import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SearchEverywhereItemData
import com.intellij.platform.searchEverywhere.SearchEverywhereParams
import com.intellij.platform.searchEverywhere.SearchEverywhereProviderId
import com.intellij.platform.searchEverywhere.SearchEverywhereTab
import com.intellij.platform.searchEverywhere.frontend.SearchEverywhereTabHelper
import com.jetbrains.rhizomedb.EID
import kotlinx.coroutines.flow.Flow

class SearchEverywhereTabMock(project: Project,
                              sessionId: EID,
                              override val name: String,
                              providerIds: List<SearchEverywhereProviderId>): SearchEverywhereTab {
  override val shortName: String = name

  private val helper = SearchEverywhereTabHelper(project, sessionId, providerIds)

  override fun getItems(params: SearchEverywhereParams): Flow<SearchEverywhereItemData> =
    helper.getItems(params)
}