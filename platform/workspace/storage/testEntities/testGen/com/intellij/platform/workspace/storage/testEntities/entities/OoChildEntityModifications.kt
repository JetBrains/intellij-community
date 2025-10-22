// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OoChildEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface OoChildEntityBuilder : WorkspaceEntityBuilder<OoChildEntity> {
  override var entitySource: EntitySource
  var childProperty: String
  var parentEntity: OoParentEntityBuilder
}

internal object OoChildEntityType : EntityType<OoChildEntity, OoChildEntityBuilder>() {
  override val entityClass: Class<OoChildEntity> get() = OoChildEntity::class.java
  operator fun invoke(
    childProperty: String,
    entitySource: EntitySource,
    init: (OoChildEntityBuilder.() -> Unit)? = null,
  ): OoChildEntityBuilder {
    val builder = builder()
    builder.childProperty = childProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOoChildEntity(
  entity: OoChildEntity,
  modification: OoChildEntityBuilder.() -> Unit,
): OoChildEntity = modifyEntity(OoChildEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createOoChildEntity")
fun OoChildEntity(
  childProperty: String,
  entitySource: EntitySource,
  init: (OoChildEntityBuilder.() -> Unit)? = null,
): OoChildEntityBuilder = OoChildEntityType(childProperty, entitySource, init)
