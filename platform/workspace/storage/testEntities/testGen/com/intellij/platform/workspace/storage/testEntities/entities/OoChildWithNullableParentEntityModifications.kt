// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OoChildWithNullableParentEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface OoChildWithNullableParentEntityBuilder : WorkspaceEntityBuilder<OoChildWithNullableParentEntity> {
  override var entitySource: EntitySource
  var parentEntity: OoParentEntityBuilder?
}

internal object OoChildWithNullableParentEntityType : EntityType<OoChildWithNullableParentEntity, OoChildWithNullableParentEntityBuilder>() {
  override val entityClass: Class<OoChildWithNullableParentEntity> get() = OoChildWithNullableParentEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (OoChildWithNullableParentEntityBuilder.() -> Unit)? = null,
  ): OoChildWithNullableParentEntityBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOoChildWithNullableParentEntity(
  entity: OoChildWithNullableParentEntity,
  modification: OoChildWithNullableParentEntityBuilder.() -> Unit,
): OoChildWithNullableParentEntity = modifyEntity(OoChildWithNullableParentEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createOoChildWithNullableParentEntity")
fun OoChildWithNullableParentEntity(
  entitySource: EntitySource,
  init: (OoChildWithNullableParentEntityBuilder.() -> Unit)? = null,
): OoChildWithNullableParentEntityBuilder = OoChildWithNullableParentEntityType(entitySource, init)
