// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.mocks

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeItemsProviderFactoryMockAlphaLocal: SeItemsProviderFactory {
  override val id: String get() = ID

  override suspend fun getItemsProvider(project: Project, dataContext: DataContext): SeItemsProvider =
    SeItemsProviderMock(resultPrefix = PREFIX, id = ID, displayName = PREFIX, delayMillis = 200, delayStep = 3)

  companion object {
    const val PREFIX: String = "AlphaLocal"
    const val ID: String = "SearchEverywhereItemsProviderMock_$PREFIX"
  }
}