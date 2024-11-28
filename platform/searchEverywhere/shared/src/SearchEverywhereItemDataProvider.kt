// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface SearchEverywhereItemDataProvider {
  fun getItems(params: SearchEverywhereParams, session: SearchEverywhereSession): Flow<SearchEverywhereItemData>
}