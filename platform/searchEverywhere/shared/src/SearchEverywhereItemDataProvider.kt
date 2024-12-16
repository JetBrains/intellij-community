// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import fleet.kernel.DurableRef
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface SearchEverywhereItemDataProvider {
  val id: SearchEverywhereProviderId

  fun getItems(sessionRef: DurableRef<SearchEverywhereSessionEntity>, params: SearchEverywhereParams): Flow<SearchEverywhereItemData>
}