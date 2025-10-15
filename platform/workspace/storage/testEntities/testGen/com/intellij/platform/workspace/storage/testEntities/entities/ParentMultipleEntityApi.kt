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
interface ModifiableParentMultipleEntity : ModifiableWorkspaceEntity<ParentMultipleEntity> {
  override var entitySource: EntitySource
  var parentData: String
  var children: List<ModifiableChildMultipleEntity>
}

internal object ParentMultipleEntityType : EntityType<ParentMultipleEntity, ModifiableParentMultipleEntity>() {
  override val entityClass: Class<ParentMultipleEntity> get() = ParentMultipleEntity::class.java
  operator fun invoke(
    parentData: String,
    entitySource: EntitySource,
    init: (ModifiableParentMultipleEntity.() -> Unit)? = null,
  ): ModifiableParentMultipleEntity {
    val builder = builder()
    builder.parentData = parentData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentMultipleEntity(
  entity: ParentMultipleEntity,
  modification: ModifiableParentMultipleEntity.() -> Unit,
): ParentMultipleEntity = modifyEntity(ModifiableParentMultipleEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentMultipleEntity")
fun ParentMultipleEntity(
  parentData: String,
  entitySource: EntitySource,
  init: (ModifiableParentMultipleEntity.() -> Unit)? = null,
): ModifiableParentMultipleEntity = ParentMultipleEntityType(parentData, entitySource, init)
