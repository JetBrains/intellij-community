// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OptionalOneToOneChildEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface OptionalOneToOneChildEntityBuilder : WorkspaceEntityBuilder<OptionalOneToOneChildEntity> {
  override var entitySource: EntitySource
  var data: String
  var parent: OptionalOneToOneParentEntityBuilder?
}

internal object OptionalOneToOneChildEntityType : EntityType<OptionalOneToOneChildEntity, OptionalOneToOneChildEntityBuilder>() {
  override val entityClass: Class<OptionalOneToOneChildEntity> get() = OptionalOneToOneChildEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (OptionalOneToOneChildEntityBuilder.() -> Unit)? = null,
  ): OptionalOneToOneChildEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOptionalOneToOneChildEntity(
  entity: OptionalOneToOneChildEntity,
  modification: OptionalOneToOneChildEntityBuilder.() -> Unit,
): OptionalOneToOneChildEntity = modifyEntity(OptionalOneToOneChildEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createOptionalOneToOneChildEntity")
fun OptionalOneToOneChildEntity(
  data: String,
  entitySource: EntitySource,
  init: (OptionalOneToOneChildEntityBuilder.() -> Unit)? = null,
): OptionalOneToOneChildEntityBuilder = OptionalOneToOneChildEntityType(data, entitySource, init)
