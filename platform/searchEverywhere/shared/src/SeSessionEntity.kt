// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.EntityType
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Serializable
class SeSessionEntity(override val eid: EID) : Entity {
  @Internal
  companion object : EntityType<SeSessionEntity>(SeSessionEntity::class, ::SeSessionEntity)
}