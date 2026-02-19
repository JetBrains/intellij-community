// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChangedComputablePropsOrderEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ChangedComputablePropsOrderEntityBuilder : WorkspaceEntityBuilder<ChangedComputablePropsOrderEntity> {
  override var entitySource: EntitySource
  var someKey: Int
  var names: MutableList<String>
  var value: Int
}

internal object ChangedComputablePropsOrderEntityType : EntityType<ChangedComputablePropsOrderEntity, ChangedComputablePropsOrderEntityBuilder>() {
  override val entityClass: Class<ChangedComputablePropsOrderEntity> get() = ChangedComputablePropsOrderEntity::class.java
  operator fun invoke(
    someKey: Int,
    names: List<String>,
    value: Int,
    entitySource: EntitySource,
    init: (ChangedComputablePropsOrderEntityBuilder.() -> Unit)? = null,
  ): ChangedComputablePropsOrderEntityBuilder {
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
  modification: ChangedComputablePropsOrderEntityBuilder.() -> Unit,
): ChangedComputablePropsOrderEntity = modifyEntity(ChangedComputablePropsOrderEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChangedComputablePropsOrderEntity")
fun ChangedComputablePropsOrderEntity(
  someKey: Int,
  names: List<String>,
  value: Int,
  entitySource: EntitySource,
  init: (ChangedComputablePropsOrderEntityBuilder.() -> Unit)? = null,
): ChangedComputablePropsOrderEntityBuilder = ChangedComputablePropsOrderEntityType(someKey, names, value, entitySource, init)
