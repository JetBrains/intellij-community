// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ListVFUEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ListVFUEntityBuilder : WorkspaceEntityBuilder<ListVFUEntity> {
  override var entitySource: EntitySource
  var data: String
  var fileProperty: MutableList<VirtualFileUrl>
}

internal object ListVFUEntityType : EntityType<ListVFUEntity, ListVFUEntityBuilder>() {
  override val entityClass: Class<ListVFUEntity> get() = ListVFUEntity::class.java
  operator fun invoke(
    data: String,
    fileProperty: List<VirtualFileUrl>,
    entitySource: EntitySource,
    init: (ListVFUEntityBuilder.() -> Unit)? = null,
  ): ListVFUEntityBuilder {
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
  modification: ListVFUEntityBuilder.() -> Unit,
): ListVFUEntity = modifyEntity(ListVFUEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createListVFUEntity")
fun ListVFUEntity(
  data: String,
  fileProperty: List<VirtualFileUrl>,
  entitySource: EntitySource,
  init: (ListVFUEntityBuilder.() -> Unit)? = null,
): ListVFUEntityBuilder = ListVFUEntityType(data, fileProperty, entitySource, init)
