// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ModifiableChangedComputablePropsOrderEntity : ModifiableWorkspaceEntity<ChangedComputablePropsOrderEntity> {
  override var entitySource: EntitySource
  var someKey: Int
  var names: MutableList<String>
  var value: Int
}

internal object ChangedComputablePropsOrderEntityType : EntityType<ChangedComputablePropsOrderEntity, ModifiableChangedComputablePropsOrderEntity>() {
  override val entityClass: Class<ChangedComputablePropsOrderEntity> get() = ChangedComputablePropsOrderEntity::class.java
  operator fun invoke(
    someKey: Int,
    names: List<String>,
    value: Int,
    entitySource: EntitySource,
    init: (ModifiableChangedComputablePropsOrderEntity.() -> Unit)? = null,
  ): ModifiableChangedComputablePropsOrderEntity {
    val builder = builder()
    builder.someKey = someKey
    builder.names = names.toMutableWorkspaceList()
    builder.value = value
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChangedComputablePropsOrderEntity(
  entity: ChangedComputablePropsOrderEntity,
  modification: ModifiableChangedComputablePropsOrderEntity.() -> Unit,
): ChangedComputablePropsOrderEntity = modifyEntity(ModifiableChangedComputablePropsOrderEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChangedComputablePropsOrderEntity")
fun ChangedComputablePropsOrderEntity(
  someKey: Int,
  names: List<String>,
  value: Int,
  entitySource: EntitySource,
  init: (ModifiableChangedComputablePropsOrderEntity.() -> Unit)? = null,
): ModifiableChangedComputablePropsOrderEntity = ChangedComputablePropsOrderEntityType(someKey, names, value, entitySource, init)
