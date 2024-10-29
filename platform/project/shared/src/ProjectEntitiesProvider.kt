// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project

import com.intellij.platform.kernel.EntityTypeProvider
import com.jetbrains.rhizomedb.EntityType

internal class ProjectEntitiesProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> {
    return listOf(
      ProjectEntity
    )
  }
}