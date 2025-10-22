// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildAbstractBaseEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface ChildAbstractBaseEntityBuilder<T : ChildAbstractBaseEntity> : WorkspaceEntityBuilder<T> {
  override var entitySource: EntitySource
  var commonData: String
  var parentEntity: ParentAbEntityBuilder
}

internal object ChildAbstractBaseEntityType : EntityType<ChildAbstractBaseEntity, ChildAbstractBaseEntityBuilder<ChildAbstractBaseEntity>>() {
  override val entityClass: Class<ChildAbstractBaseEntity> get() = ChildAbstractBaseEntity::class.java
  operator fun invoke(
    commonData: String,
    entitySource: EntitySource,
    init: (ChildAbstractBaseEntityBuilder<ChildAbstractBaseEntity>.() -> Unit)? = null,
  ): ChildAbstractBaseEntityBuilder<ChildAbstractBaseEntity> {
    val builder = builder()
    builder.commonData = commonData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
