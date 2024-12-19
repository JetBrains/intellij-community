// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.platform.searchEverywhere.SearchEverywhereItemDataProvider
import com.intellij.platform.searchEverywhere.SearchEverywhereProviderId
import com.intellij.platform.searchEverywhere.SearchEverywhereSessionEntity
import com.jetbrains.rhizomedb.*
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SearchEverywhereBackendItemDataProvidersHolderEntity(override val eid: EID) : Entity {
  val providers: Map<SearchEverywhereProviderId, SearchEverywhereItemDataProvider>
    get() = this[Providers]

  companion object : EntityType<SearchEverywhereBackendItemDataProvidersHolderEntity>(SearchEverywhereBackendItemDataProvidersHolderEntity::class.java.name, "com.intellij", {
    SearchEverywhereBackendItemDataProvidersHolderEntity(it)
  }) {
    internal val Providers = SearchEverywhereBackendItemDataProvidersHolderEntity.requiredTransient<Map<SearchEverywhereProviderId, SearchEverywhereItemDataProvider>>("providers")
    internal val Session = SearchEverywhereBackendItemDataProvidersHolderEntity.requiredRef<SearchEverywhereSessionEntity>("session", RefFlags.CASCADE_DELETE_BY)
  }
}