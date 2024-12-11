// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.jetbrains.rhizomedb.EID
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface SearchEverywhereItemDataProvider {
  val id: SearchEverywhereProviderId

  fun getItems(sessionId: EID, params: SearchEverywhereParams): Flow<SearchEverywhereItemData>
}