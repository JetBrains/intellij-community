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
interface ModifiableAttachedEntity : ModifiableWorkspaceEntity<AttachedEntity> {
  override var entitySource: EntitySource
  var ref: ModifiableMainEntity
  var data: String
}

internal object AttachedEntityType : EntityType<AttachedEntity, ModifiableAttachedEntity>() {
  override val entityClass: Class<AttachedEntity> get() = AttachedEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableAttachedEntity.() -> Unit)? = null,
  ): ModifiableAttachedEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyAttachedEntity(
  entity: AttachedEntity,
  modification: ModifiableAttachedEntity.() -> Unit,
): AttachedEntity = modifyEntity(ModifiableAttachedEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createAttachedEntity")
fun AttachedEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableAttachedEntity.() -> Unit)? = null,
): ModifiableAttachedEntity = AttachedEntityType(data, entitySource, init)
