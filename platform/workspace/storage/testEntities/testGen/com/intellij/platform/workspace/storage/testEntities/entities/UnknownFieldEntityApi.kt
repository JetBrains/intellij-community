// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import java.util.Date

@GeneratedCodeApiVersion(3)
interface ModifiableUnknownFieldEntity : ModifiableWorkspaceEntity<UnknownFieldEntity> {
  override var entitySource: EntitySource
  var data: Date
}

internal object UnknownFieldEntityType : EntityType<UnknownFieldEntity, ModifiableUnknownFieldEntity>() {
  override val entityClass: Class<UnknownFieldEntity> get() = UnknownFieldEntity::class.java
  operator fun invoke(
    data: Date,
    entitySource: EntitySource,
    init: (ModifiableUnknownFieldEntity.() -> Unit)? = null,
  ): ModifiableUnknownFieldEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyUnknownFieldEntity(
  entity: UnknownFieldEntity,
  modification: ModifiableUnknownFieldEntity.() -> Unit,
): UnknownFieldEntity = modifyEntity(ModifiableUnknownFieldEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createUnknownFieldEntity")
fun UnknownFieldEntity(
  data: Date,
  entitySource: EntitySource,
  init: (ModifiableUnknownFieldEntity.() -> Unit)? = null,
): ModifiableUnknownFieldEntity = UnknownFieldEntityType(data, entitySource, init)
