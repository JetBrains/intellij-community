// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OoParentWithoutPidEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface OoParentWithoutPidEntityBuilder : WorkspaceEntityBuilder<OoParentWithoutPidEntity> {
  override var entitySource: EntitySource
  var parentProperty: String
  var childOne: OoChildWithPidEntityBuilder?
}

internal object OoParentWithoutPidEntityType : EntityType<OoParentWithoutPidEntity, OoParentWithoutPidEntityBuilder>() {
  override val entityClass: Class<OoParentWithoutPidEntity> get() = OoParentWithoutPidEntity::class.java
  operator fun invoke(
    parentProperty: String,
    entitySource: EntitySource,
    init: (OoParentWithoutPidEntityBuilder.() -> Unit)? = null,
  ): OoParentWithoutPidEntityBuilder {
    val builder = builder()
    builder.parentProperty = parentProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOoParentWithoutPidEntity(
  entity: OoParentWithoutPidEntity,
  modification: OoParentWithoutPidEntityBuilder.() -> Unit,
): OoParentWithoutPidEntity = modifyEntity(OoParentWithoutPidEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createOoParentWithoutPidEntity")
fun OoParentWithoutPidEntity(
  parentProperty: String,
  entitySource: EntitySource,
  init: (OoParentWithoutPidEntityBuilder.() -> Unit)? = null,
): OoParentWithoutPidEntityBuilder = OoParentWithoutPidEntityType(parentProperty, entitySource, init)
