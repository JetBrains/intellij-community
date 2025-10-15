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
interface ModifiableChainedParentEntity : ModifiableWorkspaceEntity<ChainedParentEntity> {
  override var entitySource: EntitySource
  var child: List<ModifiableChainedEntity>
}

internal object ChainedParentEntityType : EntityType<ChainedParentEntity, ModifiableChainedParentEntity>() {
  override val entityClass: Class<ChainedParentEntity> get() = ChainedParentEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableChainedParentEntity.() -> Unit)? = null,
  ): ModifiableChainedParentEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChainedParentEntity(
  entity: ChainedParentEntity,
  modification: ModifiableChainedParentEntity.() -> Unit,
): ChainedParentEntity = modifyEntity(ModifiableChainedParentEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChainedParentEntity")
fun ChainedParentEntity(
  entitySource: EntitySource,
  init: (ModifiableChainedParentEntity.() -> Unit)? = null,
): ModifiableChainedParentEntity = ChainedParentEntityType(entitySource, init)
