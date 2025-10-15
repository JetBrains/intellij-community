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
interface ModifiableOptionalStringEntity : ModifiableWorkspaceEntity<OptionalStringEntity> {
  override var entitySource: EntitySource
  var data: String?
}

internal object OptionalStringEntityType : EntityType<OptionalStringEntity, ModifiableOptionalStringEntity>() {
  override val entityClass: Class<OptionalStringEntity> get() = OptionalStringEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableOptionalStringEntity.() -> Unit)? = null,
  ): ModifiableOptionalStringEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOptionalStringEntity(
  entity: OptionalStringEntity,
  modification: ModifiableOptionalStringEntity.() -> Unit,
): OptionalStringEntity = modifyEntity(ModifiableOptionalStringEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createOptionalStringEntity")
fun OptionalStringEntity(
  entitySource: EntitySource,
  init: (ModifiableOptionalStringEntity.() -> Unit)? = null,
): ModifiableOptionalStringEntity = OptionalStringEntityType(entitySource, init)
