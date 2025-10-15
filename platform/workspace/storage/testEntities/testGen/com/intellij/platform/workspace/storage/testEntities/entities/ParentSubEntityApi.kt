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
interface ModifiableParentSubEntity : ModifiableWorkspaceEntity<ParentSubEntity> {
  override var entitySource: EntitySource
  var parentData: String
  var child: ModifiableChildSubEntity?
}

internal object ParentSubEntityType : EntityType<ParentSubEntity, ModifiableParentSubEntity>() {
  override val entityClass: Class<ParentSubEntity> get() = ParentSubEntity::class.java
  operator fun invoke(
    parentData: String,
    entitySource: EntitySource,
    init: (ModifiableParentSubEntity.() -> Unit)? = null,
  ): ModifiableParentSubEntity {
    val builder = builder()
    builder.parentData = parentData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentSubEntity(
  entity: ParentSubEntity,
  modification: ModifiableParentSubEntity.() -> Unit,
): ParentSubEntity = modifyEntity(ModifiableParentSubEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentSubEntity")
fun ParentSubEntity(
  parentData: String,
  entitySource: EntitySource,
  init: (ModifiableParentSubEntity.() -> Unit)? = null,
): ModifiableParentSubEntity = ParentSubEntityType(parentData, entitySource, init)
