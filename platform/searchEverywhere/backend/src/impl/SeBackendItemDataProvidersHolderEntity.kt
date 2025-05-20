// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.providers.SeProvidersHolder
import com.jetbrains.rhizomedb.*
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeBackendItemDataProvidersHolderEntity(override val eid: EID) : Entity {
  val providersHolder: SeProvidersHolder
    get() = this[ProvidersHolder]

  @Internal
  companion object : EntityType<SeBackendItemDataProvidersHolderEntity>(SeBackendItemDataProvidersHolderEntity::class.java.name, "com.intellij", {
    SeBackendItemDataProvidersHolderEntity(it)
  }) {
    internal val ProvidersHolder = SeBackendItemDataProvidersHolderEntity.requiredTransient<SeProvidersHolder>("providersHolder")
    internal val Session = SeBackendItemDataProvidersHolderEntity.requiredRef<SeSessionEntity>("session", RefFlags.CASCADE_DELETE_BY)
  }
}