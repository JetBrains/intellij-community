// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ComputablePropEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ComputablePropEntityBuilder : WorkspaceEntityBuilder<ComputablePropEntity> {
  override var entitySource: EntitySource
  var list: MutableList<Map<List<Int?>, String>>
  var value: Int
}

internal object ComputablePropEntityType : EntityType<ComputablePropEntity, ComputablePropEntityBuilder>() {
  override val entityClass: Class<ComputablePropEntity> get() = ComputablePropEntity::class.java
  operator fun invoke(
    list: List<Map<List<Int?>, String>>,
    value: Int,
    entitySource: EntitySource,
    init: (ComputablePropEntityBuilder.() -> Unit)? = null,
  ): ComputablePropEntityBuilder {
    val builder = builder()
    builder.list = list.toMutableWorkspaceList()
    builder.value = value
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyComputablePropEntity(
  entity: ComputablePropEntity,
  modification: ComputablePropEntityBuilder.() -> Unit,
): ComputablePropEntity = modifyEntity(ComputablePropEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createComputablePropEntity")
fun ComputablePropEntity(
  list: List<Map<List<Int?>, String>>,
  value: Int,
  entitySource: EntitySource,
  init: (ComputablePropEntityBuilder.() -> Unit)? = null,
): ComputablePropEntityBuilder = ComputablePropEntityType(list, value, entitySource, init)
