// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.platform.searchEverywhere.impl.SeItemEntity
import com.intellij.platform.searchEverywhere.impl.SeItemEntityHolder
import com.jetbrains.rhizomedb.EntityType
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeEntityTypeProvider: EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> = listOf(SeSessionEntity, SeItemEntity, SeItemEntityHolder)
}