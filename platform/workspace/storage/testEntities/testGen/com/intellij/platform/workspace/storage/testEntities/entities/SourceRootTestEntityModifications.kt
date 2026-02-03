// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SourceRootTestEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface SourceRootTestEntityBuilder : WorkspaceEntityBuilder<SourceRootTestEntity> {
  override var entitySource: EntitySource
  var data: String
  var contentRoot: ContentRootTestEntityBuilder
}

internal object SourceRootTestEntityType : EntityType<SourceRootTestEntity, SourceRootTestEntityBuilder>() {
  override val entityClass: Class<SourceRootTestEntity> get() = SourceRootTestEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (SourceRootTestEntityBuilder.() -> Unit)? = null,
  ): SourceRootTestEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySourceRootTestEntity(
  entity: SourceRootTestEntity,
  modification: SourceRootTestEntityBuilder.() -> Unit,
): SourceRootTestEntity = modifyEntity(SourceRootTestEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSourceRootTestEntity")
fun SourceRootTestEntity(
  data: String,
  entitySource: EntitySource,
  init: (SourceRootTestEntityBuilder.() -> Unit)? = null,
): SourceRootTestEntityBuilder = SourceRootTestEntityType(data, entitySource, init)
