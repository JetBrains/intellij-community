// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.impl

import com.intellij.platform.kernel.withKernel
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.api.SeItem
import com.jetbrains.rhizomedb.*
import fleet.kernel.DurableRef
import fleet.kernel.change
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class SeItemEntity(override val eid: EID) : Entity {
  val item: SeItem?
    get() = this[Item] as? SeItem

  @ApiStatus.Internal
  companion object : EntityType<SeItemEntity>(SeItemEntity::class.java.name, "com.intellij", {
    SeItemEntity(it)
  }) {
    private val Item = requiredTransient<Any>("item")
    private val Session = requiredRef<SeSessionEntity>("session", RefFlags.CASCADE_DELETE_BY)

    suspend fun createWith(sessionRef: DurableRef<SeSessionEntity>, item: SeItem): SeItemEntity? {
      return withKernel {
        val session = sessionRef.derefOrNull() ?: return@withKernel null

        change {
          SeItemEntity.new {
            it[Item] = item
            it[Session] = session
          }
        }
      }
    }
  }
}