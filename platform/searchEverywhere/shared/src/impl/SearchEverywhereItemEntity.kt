// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.impl

import com.intellij.platform.kernel.withKernel
import com.intellij.platform.searchEverywhere.SearchEverywhereItem
import com.intellij.platform.searchEverywhere.SearchEverywhereSessionEntity
import com.jetbrains.rhizomedb.*
import fleet.kernel.change
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEverywhereItemEntity(override val eid: EID) : Entity {
  val item: SearchEverywhereItem?
    get() = this[Item] as? SearchEverywhereItem

  companion object : EntityType<SearchEverywhereItemEntity>(SearchEverywhereItemEntity::class.java.name, "com.intellij", {
    SearchEverywhereItemEntity(it)
  }) {
    private val Item = requiredTransient<Any>("item")
    private val Session = requiredRef<SearchEverywhereSessionEntity>("session", RefFlags.CASCADE_DELETE_BY)

    suspend fun createWith(sessionId: EID, item: SearchEverywhereItem): SearchEverywhereItemEntity? {
      return withKernel {
        val session = entity(sessionId) as? SearchEverywhereSessionEntity ?: return@withKernel null

        change {
          SearchEverywhereItemEntity.new {
            it[Item] = item
            it[Session] = session
          }
        }
      }
    }
  }
}