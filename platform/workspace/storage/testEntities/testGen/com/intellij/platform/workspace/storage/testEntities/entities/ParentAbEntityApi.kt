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
interface ModifiableParentAbEntity : ModifiableWorkspaceEntity<ParentAbEntity> {
  override var entitySource: EntitySource
  var children: List<ModifiableChildAbstractBaseEntity<out ChildAbstractBaseEntity>>
}

internal object ParentAbEntityType : EntityType<ParentAbEntity, ModifiableParentAbEntity>() {
  override val entityClass: Class<ParentAbEntity> get() = ParentAbEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableParentAbEntity.() -> Unit)? = null,
  ): ModifiableParentAbEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentAbEntity(
  entity: ParentAbEntity,
  modification: ModifiableParentAbEntity.() -> Unit,
): ParentAbEntity = modifyEntity(ModifiableParentAbEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentAbEntity")
fun ParentAbEntity(
  entitySource: EntitySource,
  init: (ModifiableParentAbEntity.() -> Unit)? = null,
): ModifiableParentAbEntity = ParentAbEntityType(entitySource, init)
