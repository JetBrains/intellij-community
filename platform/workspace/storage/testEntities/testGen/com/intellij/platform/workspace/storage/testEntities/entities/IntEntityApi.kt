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
interface ModifiableIntEntity : ModifiableWorkspaceEntity<IntEntity> {
  override var entitySource: EntitySource
  var data: Int
}

internal object IntEntityType : EntityType<IntEntity, ModifiableIntEntity>() {
  override val entityClass: Class<IntEntity> get() = IntEntity::class.java
  operator fun invoke(
    data: Int,
    entitySource: EntitySource,
    init: (ModifiableIntEntity.() -> Unit)? = null,
  ): ModifiableIntEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyIntEntity(
  entity: IntEntity,
  modification: ModifiableIntEntity.() -> Unit,
): IntEntity = modifyEntity(ModifiableIntEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createIntEntity")
fun IntEntity(
  data: Int,
  entitySource: EntitySource,
  init: (ModifiableIntEntity.() -> Unit)? = null,
): ModifiableIntEntity = IntEntityType(data, entitySource, init)
