// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChainedParentEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ChainedParentEntityBuilder : WorkspaceEntityBuilder<ChainedParentEntity> {
  override var entitySource: EntitySource
  var child: List<ChainedEntityBuilder>
}

internal object ChainedParentEntityType : EntityType<ChainedParentEntity, ChainedParentEntityBuilder>() {
  override val entityClass: Class<ChainedParentEntity> get() = ChainedParentEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ChainedParentEntityBuilder.() -> Unit)? = null,
  ): ChainedParentEntityBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChainedParentEntity(
  entity: ChainedParentEntity,
  modification: ChainedParentEntityBuilder.() -> Unit,
): ChainedParentEntity = modifyEntity(ChainedParentEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChainedParentEntity")
fun ChainedParentEntity(
  entitySource: EntitySource,
  init: (ChainedParentEntityBuilder.() -> Unit)? = null,
): ChainedParentEntityBuilder = ChainedParentEntityType(entitySource, init)
