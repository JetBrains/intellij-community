// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ModifiableBooleanEntity : ModifiableWorkspaceEntity<BooleanEntity> {
  override var entitySource: EntitySource
  var data: Boolean
}

internal object BooleanEntityType : EntityType<BooleanEntity, ModifiableBooleanEntity>() {
  override val entityClass: Class<BooleanEntity> get() = BooleanEntity::class.java
  operator fun invoke(
    data: Boolean,
    entitySource: EntitySource,
    init: (ModifiableBooleanEntity.() -> Unit)? = null,
  ): ModifiableBooleanEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyBooleanEntity(
  entity: BooleanEntity,
  modification: ModifiableBooleanEntity.() -> Unit,
): BooleanEntity = modifyEntity(ModifiableBooleanEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createBooleanEntity")
fun BooleanEntity(
  data: Boolean,
  entitySource: EntitySource,
  init: (ModifiableBooleanEntity.() -> Unit)? = null,
): ModifiableBooleanEntity = BooleanEntityType(data, entitySource, init)
