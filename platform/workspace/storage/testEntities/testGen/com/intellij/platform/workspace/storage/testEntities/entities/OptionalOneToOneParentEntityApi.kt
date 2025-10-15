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
interface ModifiableOptionalOneToOneParentEntity : ModifiableWorkspaceEntity<OptionalOneToOneParentEntity> {
  override var entitySource: EntitySource
  var child: ModifiableOptionalOneToOneChildEntity?
}

internal object OptionalOneToOneParentEntityType : EntityType<OptionalOneToOneParentEntity, ModifiableOptionalOneToOneParentEntity>() {
  override val entityClass: Class<OptionalOneToOneParentEntity> get() = OptionalOneToOneParentEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableOptionalOneToOneParentEntity.() -> Unit)? = null,
  ): ModifiableOptionalOneToOneParentEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOptionalOneToOneParentEntity(
  entity: OptionalOneToOneParentEntity,
  modification: ModifiableOptionalOneToOneParentEntity.() -> Unit,
): OptionalOneToOneParentEntity = modifyEntity(ModifiableOptionalOneToOneParentEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createOptionalOneToOneParentEntity")
fun OptionalOneToOneParentEntity(
  entitySource: EntitySource,
  init: (ModifiableOptionalOneToOneParentEntity.() -> Unit)? = null,
): ModifiableOptionalOneToOneParentEntity = OptionalOneToOneParentEntityType(entitySource, init)
