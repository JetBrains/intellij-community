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
interface ModifiableSelfLinkedEntity : ModifiableWorkspaceEntity<SelfLinkedEntity> {
  override var entitySource: EntitySource
  var parentEntity: ModifiableSelfLinkedEntity?
}

internal object SelfLinkedEntityType : EntityType<SelfLinkedEntity, ModifiableSelfLinkedEntity>() {
  override val entityClass: Class<SelfLinkedEntity> get() = SelfLinkedEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableSelfLinkedEntity.() -> Unit)? = null,
  ): ModifiableSelfLinkedEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySelfLinkedEntity(
  entity: SelfLinkedEntity,
  modification: ModifiableSelfLinkedEntity.() -> Unit,
): SelfLinkedEntity = modifyEntity(ModifiableSelfLinkedEntity::class.java, entity, modification)

var ModifiableSelfLinkedEntity.children: List<ModifiableSelfLinkedEntity>
  by WorkspaceEntity.extensionBuilder(SelfLinkedEntity::class.java)


@JvmOverloads
@JvmName("createSelfLinkedEntity")
fun SelfLinkedEntity(
  entitySource: EntitySource,
  init: (ModifiableSelfLinkedEntity.() -> Unit)? = null,
): ModifiableSelfLinkedEntity = SelfLinkedEntityType(entitySource, init)
