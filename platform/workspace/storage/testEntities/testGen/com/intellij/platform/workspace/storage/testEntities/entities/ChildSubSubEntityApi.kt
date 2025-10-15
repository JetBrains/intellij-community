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
interface ModifiableChildSubSubEntity : ModifiableWorkspaceEntity<ChildSubSubEntity> {
  override var entitySource: EntitySource
  var parentEntity: ModifiableChildSubEntity
  var childData: String
}

internal object ChildSubSubEntityType : EntityType<ChildSubSubEntity, ModifiableChildSubSubEntity>() {
  override val entityClass: Class<ChildSubSubEntity> get() = ChildSubSubEntity::class.java
  operator fun invoke(
    childData: String,
    entitySource: EntitySource,
    init: (ModifiableChildSubSubEntity.() -> Unit)? = null,
  ): ModifiableChildSubSubEntity {
    val builder = builder()
    builder.childData = childData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildSubSubEntity(
  entity: ChildSubSubEntity,
  modification: ModifiableChildSubSubEntity.() -> Unit,
): ChildSubSubEntity = modifyEntity(ModifiableChildSubSubEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildSubSubEntity")
fun ChildSubSubEntity(
  childData: String,
  entitySource: EntitySource,
  init: (ModifiableChildSubSubEntity.() -> Unit)? = null,
): ModifiableChildSubSubEntity = ChildSubSubEntityType(childData, entitySource, init)
