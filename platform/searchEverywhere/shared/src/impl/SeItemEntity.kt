// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.impl

import com.intellij.platform.kernel.withKernel
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.impl.SeItemEntityHolder.Companion.Item
import com.intellij.platform.searchEverywhere.impl.SeItemEntityHolder.Companion.ItemEntity
import com.jetbrains.rhizomedb.*
import fleet.kernel.*
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental
@Serializable
class SeItemEntity(override val eid: EID) : Entity {
  fun findItemOrNull(): SeItem? {
    return entities(ItemEntity, this).firstOrNull()?.item
  }

  @ApiStatus.Internal
  companion object : DurableEntityType<SeItemEntity>(SeItemEntity::class, ::SeItemEntity) {
    private val Session = requiredRef<SeSessionEntity>("session", RefFlags.CASCADE_DELETE_BY)

    suspend fun createWith(sessionRef: DurableRef<SeSessionEntity>, item: SeItem): DurableRef<SeItemEntity>? {
      @Suppress("DEPRECATION")
      return withKernel {
        val session = sessionRef.derefOrNull() ?: return@withKernel null

        change {
          val entity = shared {
            if (!session.exists()) return@shared null

            SeItemEntity.new {
              it[Session] = session
            }
          } ?: return@change null

          SeItemEntityHolder.new {
            it[Item] = item
            it[ItemEntity] = entity
          }

          entity
        }?.ref()
      }
    }
  }
}
