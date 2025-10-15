// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OoChildForParentWithPidEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface OoChildForParentWithPidEntityBuilder : WorkspaceEntityBuilder<OoChildForParentWithPidEntity> {
  override var entitySource: EntitySource
  var childProperty: String
  var parentEntity: OoParentWithPidEntityBuilder
}

internal object OoChildForParentWithPidEntityType : EntityType<OoChildForParentWithPidEntity, OoChildForParentWithPidEntityBuilder>() {
  override val entityClass: Class<OoChildForParentWithPidEntity> get() = OoChildForParentWithPidEntity::class.java
  operator fun invoke(
    childProperty: String,
    entitySource: EntitySource,
    init: (OoChildForParentWithPidEntityBuilder.() -> Unit)? = null,
  ): OoChildForParentWithPidEntityBuilder {
    val builder = builder()
    builder.childProperty = childProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOoChildForParentWithPidEntity(
  entity: OoChildForParentWithPidEntity,
  modification: OoChildForParentWithPidEntityBuilder.() -> Unit,
): OoChildForParentWithPidEntity = modifyEntity(OoChildForParentWithPidEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createOoChildForParentWithPidEntity")
fun OoChildForParentWithPidEntity(
  childProperty: String,
  entitySource: EntitySource,
  init: (OoChildForParentWithPidEntityBuilder.() -> Unit)? = null,
): OoChildForParentWithPidEntityBuilder = OoChildForParentWithPidEntityType(childProperty, entitySource, init)
