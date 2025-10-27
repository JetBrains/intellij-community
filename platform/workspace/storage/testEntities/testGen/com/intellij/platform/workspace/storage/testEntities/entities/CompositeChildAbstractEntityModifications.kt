// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CompositeChildAbstractEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface CompositeChildAbstractEntityBuilder : WorkspaceEntityBuilder<CompositeChildAbstractEntity>, CompositeAbstractEntityBuilder<CompositeChildAbstractEntity> {
  override var entitySource: EntitySource
  override var parentInList: CompositeAbstractEntityBuilder<out CompositeAbstractEntity>?
  override var children: List<SimpleAbstractEntityBuilder<out SimpleAbstractEntity>>
  override var parentEntity: ParentChainEntityBuilder?
}

internal object CompositeChildAbstractEntityType : EntityType<CompositeChildAbstractEntity, CompositeChildAbstractEntityBuilder>() {
  override val entityClass: Class<CompositeChildAbstractEntity> get() = CompositeChildAbstractEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (CompositeChildAbstractEntityBuilder.() -> Unit)? = null,
  ): CompositeChildAbstractEntityBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyCompositeChildAbstractEntity(
  entity: CompositeChildAbstractEntity,
  modification: CompositeChildAbstractEntityBuilder.() -> Unit,
): CompositeChildAbstractEntity = modifyEntity(CompositeChildAbstractEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createCompositeChildAbstractEntity")
fun CompositeChildAbstractEntity(
  entitySource: EntitySource,
  init: (CompositeChildAbstractEntityBuilder.() -> Unit)? = null,
): CompositeChildAbstractEntityBuilder = CompositeChildAbstractEntityType(entitySource, init)
