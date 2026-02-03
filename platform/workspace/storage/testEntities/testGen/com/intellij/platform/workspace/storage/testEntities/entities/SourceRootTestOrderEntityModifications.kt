// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SourceRootTestOrderEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface SourceRootTestOrderEntityBuilder : WorkspaceEntityBuilder<SourceRootTestOrderEntity> {
  override var entitySource: EntitySource
  var data: String
  var contentRoot: ContentRootTestEntityBuilder
}

internal object SourceRootTestOrderEntityType : EntityType<SourceRootTestOrderEntity, SourceRootTestOrderEntityBuilder>() {
  override val entityClass: Class<SourceRootTestOrderEntity> get() = SourceRootTestOrderEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (SourceRootTestOrderEntityBuilder.() -> Unit)? = null,
  ): SourceRootTestOrderEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySourceRootTestOrderEntity(
  entity: SourceRootTestOrderEntity,
  modification: SourceRootTestOrderEntityBuilder.() -> Unit,
): SourceRootTestOrderEntity = modifyEntity(SourceRootTestOrderEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSourceRootTestOrderEntity")
fun SourceRootTestOrderEntity(
  data: String,
  entitySource: EntitySource,
  init: (SourceRootTestOrderEntityBuilder.() -> Unit)? = null,
): SourceRootTestOrderEntityBuilder = SourceRootTestOrderEntityType(data, entitySource, init)
