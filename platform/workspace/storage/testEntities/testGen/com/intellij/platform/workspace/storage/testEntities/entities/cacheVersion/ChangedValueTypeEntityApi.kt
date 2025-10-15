// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ModifiableChangedValueTypeEntity : ModifiableWorkspaceEntity<ChangedValueTypeEntity> {
  override var entitySource: EntitySource
  var type: String
  var someKey: Int
  var text: MutableList<String>
}

internal object ChangedValueTypeEntityType : EntityType<ChangedValueTypeEntity, ModifiableChangedValueTypeEntity>() {
  override val entityClass: Class<ChangedValueTypeEntity> get() = ChangedValueTypeEntity::class.java
  operator fun invoke(
    type: String,
    someKey: Int,
    text: List<String>,
    entitySource: EntitySource,
    init: (ModifiableChangedValueTypeEntity.() -> Unit)? = null,
  ): ModifiableChangedValueTypeEntity {
    val builder = builder()
    builder.type = type
    builder.someKey = someKey
    builder.text = text.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChangedValueTypeEntity(
  entity: ChangedValueTypeEntity,
  modification: ModifiableChangedValueTypeEntity.() -> Unit,
): ChangedValueTypeEntity = modifyEntity(ModifiableChangedValueTypeEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChangedValueTypeEntity")
fun ChangedValueTypeEntity(
  type: String,
  someKey: Int,
  text: List<String>,
  entitySource: EntitySource,
  init: (ModifiableChangedValueTypeEntity.() -> Unit)? = null,
): ModifiableChangedValueTypeEntity = ChangedValueTypeEntityType(type, someKey, text, entitySource, init)
