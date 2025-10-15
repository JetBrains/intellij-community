// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ModifiableNullableVFUEntity : ModifiableWorkspaceEntity<NullableVFUEntity> {
  override var entitySource: EntitySource
  var data: String
  var fileProperty: VirtualFileUrl?
}

internal object NullableVFUEntityType : EntityType<NullableVFUEntity, ModifiableNullableVFUEntity>() {
  override val entityClass: Class<NullableVFUEntity> get() = NullableVFUEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableNullableVFUEntity.() -> Unit)? = null,
  ): ModifiableNullableVFUEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyNullableVFUEntity(
  entity: NullableVFUEntity,
  modification: ModifiableNullableVFUEntity.() -> Unit,
): NullableVFUEntity = modifyEntity(ModifiableNullableVFUEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createNullableVFUEntity")
fun NullableVFUEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableNullableVFUEntity.() -> Unit)? = null,
): ModifiableNullableVFUEntity = NullableVFUEntityType(data, entitySource, init)
