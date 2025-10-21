// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChangedPropsOrderEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ChangedPropsOrderEntityBuilder : WorkspaceEntityBuilder<ChangedPropsOrderEntity> {
  override var entitySource: EntitySource
  var version: Int
  var string: String
  var list: MutableList<Set<Int>>
  var data: ChangedPropsOrderDataClass
}

internal object ChangedPropsOrderEntityType : EntityType<ChangedPropsOrderEntity, ChangedPropsOrderEntityBuilder>() {
  override val entityClass: Class<ChangedPropsOrderEntity> get() = ChangedPropsOrderEntity::class.java
  operator fun invoke(
    version: Int,
    string: String,
    list: List<Set<Int>>,
    data: ChangedPropsOrderDataClass,
    entitySource: EntitySource,
    init: (ChangedPropsOrderEntityBuilder.() -> Unit)? = null,
  ): ChangedPropsOrderEntityBuilder {
    val builder = builder()
    builder.version = version
    builder.string = string
    builder.list = list.toMutableWorkspaceList()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChangedPropsOrderEntity(
  entity: ChangedPropsOrderEntity,
  modification: ChangedPropsOrderEntityBuilder.() -> Unit,
): ChangedPropsOrderEntity = modifyEntity(ChangedPropsOrderEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChangedPropsOrderEntity")
fun ChangedPropsOrderEntity(
  version: Int,
  string: String,
  list: List<Set<Int>>,
  data: ChangedPropsOrderDataClass,
  entitySource: EntitySource,
  init: (ChangedPropsOrderEntityBuilder.() -> Unit)? = null,
): ChangedPropsOrderEntityBuilder = ChangedPropsOrderEntityType(version, string, list, data, entitySource, init)
