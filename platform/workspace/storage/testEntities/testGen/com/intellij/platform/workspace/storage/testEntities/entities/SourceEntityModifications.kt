// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SourceEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface SourceEntityBuilder : WorkspaceEntityBuilder<SourceEntity> {
  override var entitySource: EntitySource
  var data: String
  var children: List<ChildSourceEntityBuilder>
}

internal object SourceEntityType : EntityType<SourceEntity, SourceEntityBuilder>() {
  override val entityClass: Class<SourceEntity> get() = SourceEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (SourceEntityBuilder.() -> Unit)? = null,
  ): SourceEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySourceEntity(
  entity: SourceEntity,
  modification: SourceEntityBuilder.() -> Unit,
): SourceEntity = modifyEntity(SourceEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSourceEntity")
fun SourceEntity(
  data: String,
  entitySource: EntitySource,
  init: (SourceEntityBuilder.() -> Unit)? = null,
): SourceEntityBuilder = SourceEntityType(data, entitySource, init)
