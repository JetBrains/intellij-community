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
interface ModifiableChildEntity : ModifiableWorkspaceEntity<ChildEntity> {
  override var entitySource: EntitySource
  var childData: String
  var parentEntity: ModifiableParentEntity
}

internal object ChildEntityType : EntityType<ChildEntity, ModifiableChildEntity>() {
  override val entityClass: Class<ChildEntity> get() = ChildEntity::class.java
  operator fun invoke(
    childData: String,
    entitySource: EntitySource,
    init: (ModifiableChildEntity.() -> Unit)? = null,
  ): ModifiableChildEntity {
    val builder = builder()
    builder.childData = childData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildEntity(
  entity: ChildEntity,
  modification: ModifiableChildEntity.() -> Unit,
): ChildEntity = modifyEntity(ModifiableChildEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildEntity")
fun ChildEntity(
  childData: String,
  entitySource: EntitySource,
  init: (ModifiableChildEntity.() -> Unit)? = null,
): ModifiableChildEntity = ChildEntityType(childData, entitySource, init)
