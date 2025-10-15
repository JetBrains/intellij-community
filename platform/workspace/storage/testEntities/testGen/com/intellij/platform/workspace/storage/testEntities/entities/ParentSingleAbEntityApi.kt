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
interface ModifiableParentSingleAbEntity : ModifiableWorkspaceEntity<ParentSingleAbEntity> {
  override var entitySource: EntitySource
  var child: ModifiableChildSingleAbstractBaseEntity<out ChildSingleAbstractBaseEntity>?
}

internal object ParentSingleAbEntityType : EntityType<ParentSingleAbEntity, ModifiableParentSingleAbEntity>() {
  override val entityClass: Class<ParentSingleAbEntity> get() = ParentSingleAbEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableParentSingleAbEntity.() -> Unit)? = null,
  ): ModifiableParentSingleAbEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentSingleAbEntity(
  entity: ParentSingleAbEntity,
  modification: ModifiableParentSingleAbEntity.() -> Unit,
): ParentSingleAbEntity = modifyEntity(ModifiableParentSingleAbEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentSingleAbEntity")
fun ParentSingleAbEntity(
  entitySource: EntitySource,
  init: (ModifiableParentSingleAbEntity.() -> Unit)? = null,
): ModifiableParentSingleAbEntity = ParentSingleAbEntityType(entitySource, init)
