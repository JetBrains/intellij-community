// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.platform.kernel.EntityTypeProvider
import com.jetbrains.rhizomedb.EntityType

class SeBackendEntityTypeProvider: EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> = listOf(SeBackendItemDataProvidersHolderEntity)
}