// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChainedEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ChainedEntityBuilder : WorkspaceEntityBuilder<ChainedEntity> {
  override var entitySource: EntitySource
  var data: String
  var parent: ChainedEntityBuilder?
  var child: ChainedEntityBuilder?
  var generalParent: ChainedParentEntityBuilder?
}

internal object ChainedEntityType : EntityType<ChainedEntity, ChainedEntityBuilder>() {
  override val entityClass: Class<ChainedEntity> get() = ChainedEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ChainedEntityBuilder.() -> Unit)? = null,
  ): ChainedEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChainedEntity(
  entity: ChainedEntity,
  modification: ChainedEntityBuilder.() -> Unit,
): ChainedEntity = modifyEntity(ChainedEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChainedEntity")
fun ChainedEntity(
  data: String,
  entitySource: EntitySource,
  init: (ChainedEntityBuilder.() -> Unit)? = null,
): ChainedEntityBuilder = ChainedEntityType(data, entitySource, init)
