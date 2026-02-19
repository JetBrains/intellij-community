// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OptionalStringEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface OptionalStringEntityBuilder : WorkspaceEntityBuilder<OptionalStringEntity> {
  override var entitySource: EntitySource
  var data: String?
}

internal object OptionalStringEntityType : EntityType<OptionalStringEntity, OptionalStringEntityBuilder>() {
  override val entityClass: Class<OptionalStringEntity> get() = OptionalStringEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (OptionalStringEntityBuilder.() -> Unit)? = null,
  ): OptionalStringEntityBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOptionalStringEntity(
  entity: OptionalStringEntity,
  modification: OptionalStringEntityBuilder.() -> Unit,
): OptionalStringEntity = modifyEntity(OptionalStringEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createOptionalStringEntity")
fun OptionalStringEntity(
  entitySource: EntitySource,
  init: (OptionalStringEntityBuilder.() -> Unit)? = null,
): OptionalStringEntityBuilder = OptionalStringEntityType(entitySource, init)
