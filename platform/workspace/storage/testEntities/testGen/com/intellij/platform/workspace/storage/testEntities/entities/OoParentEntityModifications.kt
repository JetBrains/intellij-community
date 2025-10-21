// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OoParentEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface OoParentEntityBuilder : WorkspaceEntityBuilder<OoParentEntity> {
  override var entitySource: EntitySource
  var parentProperty: String
  var child: OoChildEntityBuilder?
  var anotherChild: OoChildWithNullableParentEntityBuilder?
}

internal object OoParentEntityType : EntityType<OoParentEntity, OoParentEntityBuilder>() {
  override val entityClass: Class<OoParentEntity> get() = OoParentEntity::class.java
  operator fun invoke(
    parentProperty: String,
    entitySource: EntitySource,
    init: (OoParentEntityBuilder.() -> Unit)? = null,
  ): OoParentEntityBuilder {
    val builder = builder()
    builder.parentProperty = parentProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOoParentEntity(
  entity: OoParentEntity,
  modification: OoParentEntityBuilder.() -> Unit,
): OoParentEntity = modifyEntity(OoParentEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createOoParentEntity")
fun OoParentEntity(
  parentProperty: String,
  entitySource: EntitySource,
  init: (OoParentEntityBuilder.() -> Unit)? = null,
): OoParentEntityBuilder = OoParentEntityType(parentProperty, entitySource, init)
