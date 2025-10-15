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
interface ModifiableParentChainEntity : ModifiableWorkspaceEntity<ParentChainEntity> {
  override var entitySource: EntitySource
  var root: ModifiableCompositeAbstractEntity<out CompositeAbstractEntity>?
}

internal object ParentChainEntityType : EntityType<ParentChainEntity, ModifiableParentChainEntity>() {
  override val entityClass: Class<ParentChainEntity> get() = ParentChainEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableParentChainEntity.() -> Unit)? = null,
  ): ModifiableParentChainEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentChainEntity(
  entity: ParentChainEntity,
  modification: ModifiableParentChainEntity.() -> Unit,
): ParentChainEntity = modifyEntity(ModifiableParentChainEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentChainEntity")
fun ParentChainEntity(
  entitySource: EntitySource,
  init: (ModifiableParentChainEntity.() -> Unit)? = null,
): ModifiableParentChainEntity = ParentChainEntityType(entitySource, init)
