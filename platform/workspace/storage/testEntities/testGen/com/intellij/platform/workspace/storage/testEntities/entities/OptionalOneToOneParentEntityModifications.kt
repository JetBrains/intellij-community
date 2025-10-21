// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OptionalOneToOneParentEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface OptionalOneToOneParentEntityBuilder : WorkspaceEntityBuilder<OptionalOneToOneParentEntity> {
  override var entitySource: EntitySource
  var child: OptionalOneToOneChildEntityBuilder?
}

internal object OptionalOneToOneParentEntityType : EntityType<OptionalOneToOneParentEntity, OptionalOneToOneParentEntityBuilder>() {
  override val entityClass: Class<OptionalOneToOneParentEntity> get() = OptionalOneToOneParentEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (OptionalOneToOneParentEntityBuilder.() -> Unit)? = null,
  ): OptionalOneToOneParentEntityBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOptionalOneToOneParentEntity(
  entity: OptionalOneToOneParentEntity,
  modification: OptionalOneToOneParentEntityBuilder.() -> Unit,
): OptionalOneToOneParentEntity = modifyEntity(OptionalOneToOneParentEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createOptionalOneToOneParentEntity")
fun OptionalOneToOneParentEntity(
  entitySource: EntitySource,
  init: (OptionalOneToOneParentEntityBuilder.() -> Unit)? = null,
): OptionalOneToOneParentEntityBuilder = OptionalOneToOneParentEntityType(entitySource, init)
