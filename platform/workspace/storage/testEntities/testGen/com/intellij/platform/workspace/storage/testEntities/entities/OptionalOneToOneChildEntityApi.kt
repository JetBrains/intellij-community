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
interface ModifiableOptionalOneToOneChildEntity : ModifiableWorkspaceEntity<OptionalOneToOneChildEntity> {
  override var entitySource: EntitySource
  var data: String
  var parent: ModifiableOptionalOneToOneParentEntity?
}

internal object OptionalOneToOneChildEntityType : EntityType<OptionalOneToOneChildEntity, ModifiableOptionalOneToOneChildEntity>() {
  override val entityClass: Class<OptionalOneToOneChildEntity> get() = OptionalOneToOneChildEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableOptionalOneToOneChildEntity.() -> Unit)? = null,
  ): ModifiableOptionalOneToOneChildEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOptionalOneToOneChildEntity(
  entity: OptionalOneToOneChildEntity,
  modification: ModifiableOptionalOneToOneChildEntity.() -> Unit,
): OptionalOneToOneChildEntity = modifyEntity(ModifiableOptionalOneToOneChildEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createOptionalOneToOneChildEntity")
fun OptionalOneToOneChildEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableOptionalOneToOneChildEntity.() -> Unit)? = null,
): ModifiableOptionalOneToOneChildEntity = OptionalOneToOneChildEntityType(data, entitySource, init)
