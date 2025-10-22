// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NamedChildEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface NamedChildEntityBuilder : WorkspaceEntityBuilder<NamedChildEntity> {
  override var entitySource: EntitySource
  var childProperty: String
  var parentEntity: NamedEntityBuilder
}

internal object NamedChildEntityType : EntityType<NamedChildEntity, NamedChildEntityBuilder>() {
  override val entityClass: Class<NamedChildEntity> get() = NamedChildEntity::class.java
  operator fun invoke(
    childProperty: String,
    entitySource: EntitySource,
    init: (NamedChildEntityBuilder.() -> Unit)? = null,
  ): NamedChildEntityBuilder {
    val builder = builder()
    builder.childProperty = childProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyNamedChildEntity(
  entity: NamedChildEntity,
  modification: NamedChildEntityBuilder.() -> Unit,
): NamedChildEntity = modifyEntity(NamedChildEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createNamedChildEntity")
fun NamedChildEntity(
  childProperty: String,
  entitySource: EntitySource,
  init: (NamedChildEntityBuilder.() -> Unit)? = null,
): NamedChildEntityBuilder = NamedChildEntityType(childProperty, entitySource, init)
