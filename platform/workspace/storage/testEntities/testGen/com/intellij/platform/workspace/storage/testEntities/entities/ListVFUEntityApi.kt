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
interface ModifiableListVFUEntity : ModifiableWorkspaceEntity<ListVFUEntity> {
  override var entitySource: EntitySource
  var data: String
  var fileProperty: MutableList<VirtualFileUrl>
}

internal object ListVFUEntityType : EntityType<ListVFUEntity, ModifiableListVFUEntity>() {
  override val entityClass: Class<ListVFUEntity> get() = ListVFUEntity::class.java
  operator fun invoke(
    data: String,
    fileProperty: List<VirtualFileUrl>,
    entitySource: EntitySource,
    init: (ModifiableListVFUEntity.() -> Unit)? = null,
  ): ModifiableListVFUEntity {
    val builder = builder()
    builder.data = data
    builder.fileProperty = fileProperty.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyListVFUEntity(
  entity: ListVFUEntity,
  modification: ModifiableListVFUEntity.() -> Unit,
): ListVFUEntity = modifyEntity(ModifiableListVFUEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createListVFUEntity")
fun ListVFUEntity(
  data: String,
  fileProperty: List<VirtualFileUrl>,
  entitySource: EntitySource,
  init: (ModifiableListVFUEntity.() -> Unit)? = null,
): ModifiableListVFUEntity = ListVFUEntityType(data, fileProperty, entitySource, init)
