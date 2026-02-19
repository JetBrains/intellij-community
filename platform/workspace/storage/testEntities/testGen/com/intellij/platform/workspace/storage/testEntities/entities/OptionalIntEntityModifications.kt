// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OptionalIntEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface OptionalIntEntityBuilder : WorkspaceEntityBuilder<OptionalIntEntity> {
  override var entitySource: EntitySource
  var data: Int?
}

internal object OptionalIntEntityType : EntityType<OptionalIntEntity, OptionalIntEntityBuilder>() {
  override val entityClass: Class<OptionalIntEntity> get() = OptionalIntEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (OptionalIntEntityBuilder.() -> Unit)? = null,
  ): OptionalIntEntityBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOptionalIntEntity(
  entity: OptionalIntEntity,
  modification: OptionalIntEntityBuilder.() -> Unit,
): OptionalIntEntity = modifyEntity(OptionalIntEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createOptionalIntEntity")
fun OptionalIntEntity(
  entitySource: EntitySource,
  init: (OptionalIntEntityBuilder.() -> Unit)? = null,
): OptionalIntEntityBuilder = OptionalIntEntityType(entitySource, init)
