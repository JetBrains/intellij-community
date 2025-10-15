// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableChainedEntity : ModifiableWorkspaceEntity<ChainedEntity> {
  override var entitySource: EntitySource
  var data: String
  var parent: ModifiableChainedEntity?
  var child: ModifiableChainedEntity?
  var generalParent: ModifiableChainedParentEntity?
}

internal object ChainedEntityType : EntityType<ChainedEntity, ModifiableChainedEntity>() {
  override val entityClass: Class<ChainedEntity> get() = ChainedEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableChainedEntity.() -> Unit)? = null,
  ): ModifiableChainedEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChainedEntity(
  entity: ChainedEntity,
  modification: ModifiableChainedEntity.() -> Unit,
): ChainedEntity = modifyEntity(ModifiableChainedEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChainedEntity")
fun ChainedEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableChainedEntity.() -> Unit)? = null,
): ModifiableChainedEntity = ChainedEntityType(data, entitySource, init)
