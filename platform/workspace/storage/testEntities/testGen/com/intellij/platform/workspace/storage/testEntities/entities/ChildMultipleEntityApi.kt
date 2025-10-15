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
interface ModifiableChildMultipleEntity : ModifiableWorkspaceEntity<ChildMultipleEntity> {
  override var entitySource: EntitySource
  var childData: String
  var parentEntity: ModifiableParentMultipleEntity
}

internal object ChildMultipleEntityType : EntityType<ChildMultipleEntity, ModifiableChildMultipleEntity>() {
  override val entityClass: Class<ChildMultipleEntity> get() = ChildMultipleEntity::class.java
  operator fun invoke(
    childData: String,
    entitySource: EntitySource,
    init: (ModifiableChildMultipleEntity.() -> Unit)? = null,
  ): ModifiableChildMultipleEntity {
    val builder = builder()
    builder.childData = childData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildMultipleEntity(
  entity: ChildMultipleEntity,
  modification: ModifiableChildMultipleEntity.() -> Unit,
): ChildMultipleEntity = modifyEntity(ModifiableChildMultipleEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildMultipleEntity")
fun ChildMultipleEntity(
  childData: String,
  entitySource: EntitySource,
  init: (ModifiableChildMultipleEntity.() -> Unit)? = null,
): ModifiableChildMultipleEntity = ChildMultipleEntityType(childData, entitySource, init)
