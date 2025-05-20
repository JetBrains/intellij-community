// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SeWrappedLegacyContributorItemsProviderFactory: SeItemsProviderFactory {
  suspend fun getItemsProvider(legacyContributor: SearchEverywhereContributor<Any>): SeItemsProvider?
}