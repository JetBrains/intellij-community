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
interface ModifiableParentWithExtensionEntity : ModifiableWorkspaceEntity<ParentWithExtensionEntity> {
  override var entitySource: EntitySource
  var data: String
}

internal object ParentWithExtensionEntityType : EntityType<ParentWithExtensionEntity, ModifiableParentWithExtensionEntity>() {
  override val entityClass: Class<ParentWithExtensionEntity> get() = ParentWithExtensionEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableParentWithExtensionEntity.() -> Unit)? = null,
  ): ModifiableParentWithExtensionEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentWithExtensionEntity(
  entity: ParentWithExtensionEntity,
  modification: ModifiableParentWithExtensionEntity.() -> Unit,
): ParentWithExtensionEntity = modifyEntity(ModifiableParentWithExtensionEntity::class.java, entity, modification)

var ModifiableParentWithExtensionEntity.child: ModifiableAbstractChildEntity<out AbstractChildEntity>?
  by WorkspaceEntity.extensionBuilder(AbstractChildEntity::class.java)


@JvmOverloads
@JvmName("createParentWithExtensionEntity")
fun ParentWithExtensionEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableParentWithExtensionEntity.() -> Unit)? = null,
): ModifiableParentWithExtensionEntity = ParentWithExtensionEntityType(data, entitySource, init)
