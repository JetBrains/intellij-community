// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.api.SeItemsProvider
import com.intellij.platform.searchEverywhere.api.SeItemsProviderFactory
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeItemsProviderFactoryMockBetaLocal: SeItemsProviderFactory {
  override val id: String get() = ID

  override fun getItemsProvider(project: Project, dataContext: DataContext): SeItemsProvider =
    SeItemsProviderMock(resultPrefix = PREFIX, id = ID, delayMillis = 400, delayStep = 5)

  companion object {
    const val PREFIX: String = "BetaLocal"
    const val ID: String = "SearchEverywhereItemsProviderMock_$PREFIX"
  }
}