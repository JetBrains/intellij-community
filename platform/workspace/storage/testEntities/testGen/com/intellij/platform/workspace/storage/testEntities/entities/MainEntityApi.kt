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
interface ModifiableMainEntity : ModifiableWorkspaceEntity<MainEntity> {
  override var entitySource: EntitySource
  var x: String
}

internal object MainEntityType : EntityType<MainEntity, ModifiableMainEntity>() {
  override val entityClass: Class<MainEntity> get() = MainEntity::class.java
  operator fun invoke(
    x: String,
    entitySource: EntitySource,
    init: (ModifiableMainEntity.() -> Unit)? = null,
  ): ModifiableMainEntity {
    val builder = builder()
    builder.x = x
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyMainEntity(
  entity: MainEntity,
  modification: ModifiableMainEntity.() -> Unit,
): MainEntity = modifyEntity(ModifiableMainEntity::class.java, entity, modification)

var ModifiableMainEntity.child: ModifiableAttachedEntity?
  by WorkspaceEntity.extensionBuilder(AttachedEntity::class.java)


@JvmOverloads
@JvmName("createMainEntity")
fun MainEntity(
  x: String,
  entitySource: EntitySource,
  init: (ModifiableMainEntity.() -> Unit)? = null,
): ModifiableMainEntity = MainEntityType(x, entitySource, init)
