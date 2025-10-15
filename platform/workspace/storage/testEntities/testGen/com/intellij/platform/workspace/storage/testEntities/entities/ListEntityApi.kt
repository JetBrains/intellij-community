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
interface ModifiableListEntity : ModifiableWorkspaceEntity<ListEntity> {
  override var entitySource: EntitySource
  var data: MutableList<String>
}

internal object ListEntityType : EntityType<ListEntity, ModifiableListEntity>() {
  override val entityClass: Class<ListEntity> get() = ListEntity::class.java
  operator fun invoke(
    data: List<String>,
    entitySource: EntitySource,
    init: (ModifiableListEntity.() -> Unit)? = null,
  ): ModifiableListEntity {
    val builder = builder()
    builder.data = data.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyListEntity(
  entity: ListEntity,
  modification: ModifiableListEntity.() -> Unit,
): ListEntity = modifyEntity(ModifiableListEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createListEntity")
fun ListEntity(
  data: List<String>,
  entitySource: EntitySource,
  init: (ModifiableListEntity.() -> Unit)? = null,
): ModifiableListEntity = ListEntityType(data, entitySource, init)
