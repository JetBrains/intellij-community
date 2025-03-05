// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.api.SeItemDataProvider
import com.jetbrains.rhizomedb.*
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeBackendItemDataProvidersHolderEntity(override val eid: EID) : Entity {
  val providers: Map<SeProviderId, SeItemDataProvider>
    get() = this[Providers]

  @Internal
  companion object : EntityType<SeBackendItemDataProvidersHolderEntity>(SeBackendItemDataProvidersHolderEntity::class.java.name, "com.intellij", {
    SeBackendItemDataProvidersHolderEntity(it)
  }) {
    internal val Providers = SeBackendItemDataProvidersHolderEntity.requiredTransient<Map<SeProviderId, SeItemDataProvider>>("providers")
    internal val Session = SeBackendItemDataProvidersHolderEntity.requiredRef<SeSessionEntity>("session", RefFlags.CASCADE_DELETE_BY)
  }
}