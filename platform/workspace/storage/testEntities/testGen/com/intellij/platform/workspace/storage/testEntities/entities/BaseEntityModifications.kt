// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("BaseEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface BaseEntityBuilder<T : BaseEntity> : WorkspaceEntityBuilder<T> {
  override var entitySource: EntitySource
  var parentEntity: CompositeBaseEntityBuilder<out CompositeBaseEntity>?
}

internal object BaseEntityType : EntityType<BaseEntity, BaseEntityBuilder<BaseEntity>>() {
  override val entityClass: Class<BaseEntity> get() = BaseEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (BaseEntityBuilder<BaseEntity>.() -> Unit)? = null,
  ): BaseEntityBuilder<BaseEntity> {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
