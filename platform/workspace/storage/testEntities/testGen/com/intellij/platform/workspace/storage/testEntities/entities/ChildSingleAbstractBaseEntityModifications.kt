// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildSingleAbstractBaseEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface ChildSingleAbstractBaseEntityBuilder<T : ChildSingleAbstractBaseEntity> : WorkspaceEntityBuilder<T> {
  override var entitySource: EntitySource
  var commonData: String
  var parentEntity: ParentSingleAbEntityBuilder
}

internal object ChildSingleAbstractBaseEntityType : EntityType<ChildSingleAbstractBaseEntity, ChildSingleAbstractBaseEntityBuilder<ChildSingleAbstractBaseEntity>>() {
  override val entityClass: Class<ChildSingleAbstractBaseEntity> get() = ChildSingleAbstractBaseEntity::class.java
  operator fun invoke(
    commonData: String,
    entitySource: EntitySource,
    init: (ChildSingleAbstractBaseEntityBuilder<ChildSingleAbstractBaseEntity>.() -> Unit)? = null,
  ): ChildSingleAbstractBaseEntityBuilder<ChildSingleAbstractBaseEntity> {
    val builder = builder()
    builder.commonData = commonData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
