// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.impl

import com.intellij.platform.kernel.withKernel
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.api.SeItem
import com.intellij.platform.searchEverywhere.impl.SeItemEntityHolder.Companion.Entity
import com.intellij.platform.searchEverywhere.impl.SeItemEntityHolder.Companion.Item
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.RefFlags
import com.jetbrains.rhizomedb.entities
import fleet.kernel.*
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental
@Serializable
class SeItemEntity(override val eid: EID) : Entity {
  suspend fun findItemOrNull(): SeItem? {
    val entity = this
    return withKernel {
      entities(Entity, entity).firstOrNull()
    }?.item
  }

  @ApiStatus.Internal
  companion object : DurableEntityType<SeItemEntity>(SeItemEntity::class, ::SeItemEntity) {
    private val Session = requiredRef<SeSessionEntity>("session", RefFlags.CASCADE_DELETE_BY)

    suspend fun createWith(sessionRef: DurableRef<SeSessionEntity>, item: SeItem): DurableRef<SeItemEntity>? {
      return withKernel {
        val session = sessionRef.derefOrNull() ?: return@withKernel null

        change {
          val entity = shared {
            SeItemEntity.new {
              it[Session] = session
            }
          }

          SeItemEntityHolder.new {
            it[Item] = item
            it[Entity] = entity
          }

          entity
        }.ref()
      }
    }
  }
}
