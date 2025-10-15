// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CompositeBaseEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface CompositeBaseEntityBuilder<T : CompositeBaseEntity> : WorkspaceEntityBuilder<T>, BaseEntityBuilder<T> {
  override var entitySource: EntitySource
  override var parentEntity: CompositeBaseEntityBuilder<out CompositeBaseEntity>?
  var children: List<BaseEntityBuilder<out BaseEntity>>
  var parent: HeadAbstractionEntityBuilder?
}

internal object CompositeBaseEntityType : EntityType<CompositeBaseEntity, CompositeBaseEntityBuilder<CompositeBaseEntity>>() {
  override val entityClass: Class<CompositeBaseEntity> get() = CompositeBaseEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (CompositeBaseEntityBuilder<CompositeBaseEntity>.() -> Unit)? = null,
  ): CompositeBaseEntityBuilder<CompositeBaseEntity> {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
