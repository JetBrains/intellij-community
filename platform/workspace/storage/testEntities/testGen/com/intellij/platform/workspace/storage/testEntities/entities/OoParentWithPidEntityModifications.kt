// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OoParentWithPidEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface OoParentWithPidEntityBuilder : WorkspaceEntityBuilder<OoParentWithPidEntity> {
  override var entitySource: EntitySource
  var parentProperty: String
  var childOne: OoChildForParentWithPidEntityBuilder?
  var childThree: OoChildAlsoWithPidEntityBuilder?
}

internal object OoParentWithPidEntityType : EntityType<OoParentWithPidEntity, OoParentWithPidEntityBuilder>() {
  override val entityClass: Class<OoParentWithPidEntity> get() = OoParentWithPidEntity::class.java
  operator fun invoke(
    parentProperty: String,
    entitySource: EntitySource,
    init: (OoParentWithPidEntityBuilder.() -> Unit)? = null,
  ): OoParentWithPidEntityBuilder {
    val builder = builder()
    builder.parentProperty = parentProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOoParentWithPidEntity(
  entity: OoParentWithPidEntity,
  modification: OoParentWithPidEntityBuilder.() -> Unit,
): OoParentWithPidEntity = modifyEntity(OoParentWithPidEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createOoParentWithPidEntity")
fun OoParentWithPidEntity(
  parentProperty: String,
  entitySource: EntitySource,
  init: (OoParentWithPidEntityBuilder.() -> Unit)? = null,
): OoParentWithPidEntityBuilder = OoParentWithPidEntityType(parentProperty, entitySource, init)
