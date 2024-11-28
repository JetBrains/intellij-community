// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.platform.searchEverywhere.SearchEverywhereItemDataProvider
import com.intellij.platform.searchEverywhere.SearchEverywhereProviderId
import com.intellij.platform.searchEverywhere.impl.SearchEverywhereBaseDispatcher
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEverywhereFrontendDispatcher(
  providers: List<SearchEverywhereItemDataProvider>,
  providersAndLimits: Map<SearchEverywhereProviderId, Int>,
): SearchEverywhereBaseDispatcher(providers, providersAndLimits)