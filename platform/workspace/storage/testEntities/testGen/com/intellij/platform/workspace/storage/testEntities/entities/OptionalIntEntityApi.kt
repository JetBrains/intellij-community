// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ModifiableOptionalIntEntity : ModifiableWorkspaceEntity<OptionalIntEntity> {
  override var entitySource: EntitySource
  var data: Int?
}

internal object OptionalIntEntityType : EntityType<OptionalIntEntity, ModifiableOptionalIntEntity>() {
  override val entityClass: Class<OptionalIntEntity> get() = OptionalIntEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableOptionalIntEntity.() -> Unit)? = null,
  ): ModifiableOptionalIntEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOptionalIntEntity(
  entity: OptionalIntEntity,
  modification: ModifiableOptionalIntEntity.() -> Unit,
): OptionalIntEntity = modifyEntity(ModifiableOptionalIntEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createOptionalIntEntity")
fun OptionalIntEntity(
  entitySource: EntitySource,
  init: (ModifiableOptionalIntEntity.() -> Unit)? = null,
): ModifiableOptionalIntEntity = OptionalIntEntityType(entitySource, init)
