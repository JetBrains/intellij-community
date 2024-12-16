// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.mocks

import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SearchEverywhereProviderId
import com.intellij.platform.searchEverywhere.SearchEverywhereSessionEntity
import com.intellij.platform.searchEverywhere.SearchEverywhereTab
import com.intellij.platform.searchEverywhere.SearchEverywhereTabProvider
import fleet.kernel.DurableRef

class SearchEverywhereTabProviderMockCharlieRemote : SearchEverywhereTabProvider {
  override fun getTab(project: Project, sessionRef: DurableRef<SearchEverywhereSessionEntity>): SearchEverywhereTab =
    SearchEverywhereTabMock(project,
                            sessionRef,
                            "Charlie-Remote",
                            listOf(SearchEverywhereProviderId("SearchEverywhereItemsProviderMock_MockBackend")))
}