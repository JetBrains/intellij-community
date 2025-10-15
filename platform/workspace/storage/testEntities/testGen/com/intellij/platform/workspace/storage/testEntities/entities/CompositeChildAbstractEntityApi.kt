// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableCompositeChildAbstractEntity : ModifiableWorkspaceEntity<CompositeChildAbstractEntity>, ModifiableCompositeAbstractEntity<CompositeChildAbstractEntity> {
  override var entitySource: EntitySource
  override var parentInList: ModifiableCompositeAbstractEntity<out CompositeAbstractEntity>?
  override var children: List<ModifiableSimpleAbstractEntity<out SimpleAbstractEntity>>
  override var parentEntity: ModifiableParentChainEntity?
}

internal object CompositeChildAbstractEntityType : EntityType<CompositeChildAbstractEntity, ModifiableCompositeChildAbstractEntity>() {
  override val entityClass: Class<CompositeChildAbstractEntity> get() = CompositeChildAbstractEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableCompositeChildAbstractEntity.() -> Unit)? = null,
  ): ModifiableCompositeChildAbstractEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyCompositeChildAbstractEntity(
  entity: CompositeChildAbstractEntity,
  modification: ModifiableCompositeChildAbstractEntity.() -> Unit,
): CompositeChildAbstractEntity = modifyEntity(ModifiableCompositeChildAbstractEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createCompositeChildAbstractEntity")
fun CompositeChildAbstractEntity(
  entitySource: EntitySource,
  init: (ModifiableCompositeChildAbstractEntity.() -> Unit)? = null,
): ModifiableCompositeChildAbstractEntity = CompositeChildAbstractEntityType(entitySource, init)
