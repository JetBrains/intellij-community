// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.mocks

import com.intellij.platform.searchEverywhere.SearchEverywhereItemsProvider
import com.intellij.platform.searchEverywhere.SearchEverywhereItemsProviderFactory
import com.intellij.platform.searchEverywhere.mocks.SearchEverywhereItemsProviderMock

class SearchEverywhereItemsProviderFactoryMockBackend: SearchEverywhereItemsProviderFactory {
  override fun getItemsProvider(): SearchEverywhereItemsProvider =
    SearchEverywhereItemsProviderMock(resultPrefix = PREFIX, id = ID, delayMillis = 1500, delayStep = 5)

  companion object {
    const val PREFIX: String = "MockBackend"
    const val ID: String = "SearchEverywhereItemsProviderMock_$PREFIX"
  }
}