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
interface ModifiableChildSubEntity : ModifiableWorkspaceEntity<ChildSubEntity> {
  override var entitySource: EntitySource
  var parentEntity: ModifiableParentSubEntity
  var child: ModifiableChildSubSubEntity?
}

internal object ChildSubEntityType : EntityType<ChildSubEntity, ModifiableChildSubEntity>() {
  override val entityClass: Class<ChildSubEntity> get() = ChildSubEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableChildSubEntity.() -> Unit)? = null,
  ): ModifiableChildSubEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildSubEntity(
  entity: ChildSubEntity,
  modification: ModifiableChildSubEntity.() -> Unit,
): ChildSubEntity = modifyEntity(ModifiableChildSubEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildSubEntity")
fun ChildSubEntity(
  entitySource: EntitySource,
  init: (ModifiableChildSubEntity.() -> Unit)? = null,
): ModifiableChildSubEntity = ChildSubEntityType(entitySource, init)
