// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.mocks

import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SearchEverywhereItemsProvider
import com.intellij.platform.searchEverywhere.SearchEverywhereItemsProviderFactory

class SearchEverywhereItemsProviderFactoryMockBetaLocal: SearchEverywhereItemsProviderFactory {
  override fun getItemsProvider(project: Project): SearchEverywhereItemsProvider =
    SearchEverywhereItemsProviderMock(resultPrefix = PREFIX, id = ID, delayMillis = 400, delayStep = 5)

  companion object {
    const val PREFIX: String = "BetaLocal"
    const val ID: String = "SearchEverywhereItemsProviderMock_$PREFIX"
  }
}