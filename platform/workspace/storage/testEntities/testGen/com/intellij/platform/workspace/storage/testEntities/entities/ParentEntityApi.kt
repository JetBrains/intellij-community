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
interface ModifiableParentEntity : ModifiableWorkspaceEntity<ParentEntity> {
  override var entitySource: EntitySource
  var parentData: String
  var child: ModifiableChildEntity?
}

internal object ParentEntityType : EntityType<ParentEntity, ModifiableParentEntity>() {
  override val entityClass: Class<ParentEntity> get() = ParentEntity::class.java
  operator fun invoke(
    parentData: String,
    entitySource: EntitySource,
    init: (ModifiableParentEntity.() -> Unit)? = null,
  ): ModifiableParentEntity {
    val builder = builder()
    builder.parentData = parentData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentEntity(
  entity: ParentEntity,
  modification: ModifiableParentEntity.() -> Unit,
): ParentEntity = modifyEntity(ModifiableParentEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentEntity")
fun ParentEntity(
  parentData: String,
  entitySource: EntitySource,
  init: (ModifiableParentEntity.() -> Unit)? = null,
): ModifiableParentEntity = ParentEntityType(parentData, entitySource, init)
