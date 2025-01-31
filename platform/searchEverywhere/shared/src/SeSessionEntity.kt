// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.codeWithMe.ClientId
import com.intellij.platform.kernel.withKernel
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import fleet.kernel.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Serializable
class SeSessionEntity(override val eid: EID) : Entity {
  val clientId: ClientId get() = ClientId(ClientIdString(this))
  val actionId: String get() = ActionId(this)

  @Internal
  companion object : DurableEntityType<SeSessionEntity>(SeSessionEntity::class, ::SeSessionEntity) {
    private val ClientIdString = SeSessionEntity.requiredValue("clientId", String.serializer())
    private val ActionId = SeSessionEntity.requiredValue("actionId", String.serializer())

    suspend fun createRefWith(clientId: ClientId, actionId: String): DurableRef<SeSessionEntity> = withKernel {
      change {
        shared {
          SeSessionEntity.new {
            it[ClientIdString] = clientId.value
            it[ActionId] = actionId
          }
        }
      }.ref()
    }
  }
}