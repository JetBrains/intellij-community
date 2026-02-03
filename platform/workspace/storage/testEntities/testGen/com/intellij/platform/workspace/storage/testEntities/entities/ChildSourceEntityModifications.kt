// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildSourceEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface ChildSourceEntityBuilder : WorkspaceEntityBuilder<ChildSourceEntity> {
  override var entitySource: EntitySource
  var data: String
  var parentEntity: SourceEntityBuilder
}

internal object ChildSourceEntityType : EntityType<ChildSourceEntity, ChildSourceEntityBuilder>() {
  override val entityClass: Class<ChildSourceEntity> get() = ChildSourceEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ChildSourceEntityBuilder.() -> Unit)? = null,
  ): ChildSourceEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildSourceEntity(
  entity: ChildSourceEntity,
  modification: ChildSourceEntityBuilder.() -> Unit,
): ChildSourceEntity = modifyEntity(ChildSourceEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildSourceEntity")
fun ChildSourceEntity(
  data: String,
  entitySource: EntitySource,
  init: (ChildSourceEntityBuilder.() -> Unit)? = null,
): ChildSourceEntityBuilder = ChildSourceEntityType(data, entitySource, init)
