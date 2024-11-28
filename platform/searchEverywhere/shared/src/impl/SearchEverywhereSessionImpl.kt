// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.impl

import com.intellij.platform.searchEverywhere.SearchEverywhereItem
import com.intellij.platform.searchEverywhere.SearchEverywhereItemId
import com.intellij.platform.searchEverywhere.SearchEverywhereSession
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*

@Internal
class SearchEverywhereSessionImpl : SearchEverywhereSession {
  override val id: String = UUID.randomUUID().toString()

  private val items = mutableMapOf<SearchEverywhereItemId, SearchEverywhereItem>()

  override suspend fun saveItem(item: SearchEverywhereItem): SearchEverywhereItemId {
    // TODO: Save properly to DB
    val id = SearchEverywhereItemId(UUID.randomUUID().toString())
    items[id] = item
    return id
  }

  override suspend fun getItem(itemId: SearchEverywhereItemId): SearchEverywhereItem? {
    // TODO: Get properly from DB
    return items[itemId]
  }

  override suspend fun dispose() {
    // TODO: Clear properly from DB
    items.clear()
  }
}