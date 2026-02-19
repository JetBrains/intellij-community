// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("MiddleEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface MiddleEntityBuilder : WorkspaceEntityBuilder<MiddleEntity>, BaseEntityBuilder<MiddleEntity> {
  override var entitySource: EntitySource
  override var parentEntity: CompositeBaseEntityBuilder<out CompositeBaseEntity>?
  var property: String
}

internal object MiddleEntityType : EntityType<MiddleEntity, MiddleEntityBuilder>() {
  override val entityClass: Class<MiddleEntity> get() = MiddleEntity::class.java
  operator fun invoke(
    property: String,
    entitySource: EntitySource,
    init: (MiddleEntityBuilder.() -> Unit)? = null,
  ): MiddleEntityBuilder {
    val builder = builder()
    builder.property = property
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyMiddleEntity(
  entity: MiddleEntity,
  modification: MiddleEntityBuilder.() -> Unit,
): MiddleEntity = modifyEntity(MiddleEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createMiddleEntity")
fun MiddleEntity(
  property: String,
  entitySource: EntitySource,
  init: (MiddleEntityBuilder.() -> Unit)? = null,
): MiddleEntityBuilder = MiddleEntityType(property, entitySource, init)
