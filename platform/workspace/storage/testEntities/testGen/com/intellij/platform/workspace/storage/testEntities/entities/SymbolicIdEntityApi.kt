// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.util.UUID

@GeneratedCodeApiVersion(3)
interface ModifiableSymbolicIdEntity : ModifiableWorkspaceEntity<SymbolicIdEntity> {
  override var entitySource: EntitySource
  var data: String
}

internal object SymbolicIdEntityType : EntityType<SymbolicIdEntity, ModifiableSymbolicIdEntity>() {
  override val entityClass: Class<SymbolicIdEntity> get() = SymbolicIdEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableSymbolicIdEntity.() -> Unit)? = null,
  ): ModifiableSymbolicIdEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySymbolicIdEntity(
  entity: SymbolicIdEntity,
  modification: ModifiableSymbolicIdEntity.() -> Unit,
): SymbolicIdEntity = modifyEntity(ModifiableSymbolicIdEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSymbolicIdEntity")
fun SymbolicIdEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableSymbolicIdEntity.() -> Unit)? = null,
): ModifiableSymbolicIdEntity = SymbolicIdEntityType(data, entitySource, init)
