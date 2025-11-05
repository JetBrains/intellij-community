// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ListEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ListEntityBuilder : WorkspaceEntityBuilder<ListEntity> {
  override var entitySource: EntitySource
  var data: MutableList<String>
}

internal object ListEntityType : EntityType<ListEntity, ListEntityBuilder>() {
  override val entityClass: Class<ListEntity> get() = ListEntity::class.java
  operator fun invoke(
    data: List<String>,
    entitySource: EntitySource,
    init: (ListEntityBuilder.() -> Unit)? = null,
  ): ListEntityBuilder {
    val builder = builder()
    builder.data = data.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyListEntity(
  entity: ListEntity,
  modification: ListEntityBuilder.() -> Unit,
): ListEntity = modifyEntity(ListEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createListEntity")
fun ListEntity(
  data: List<String>,
  entitySource: EntitySource,
  init: (ListEntityBuilder.() -> Unit)? = null,
): ListEntityBuilder = ListEntityType(data, entitySource, init)
