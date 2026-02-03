// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OoChildWithPidEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface OoChildWithPidEntityBuilder : WorkspaceEntityBuilder<OoChildWithPidEntity> {
  override var entitySource: EntitySource
  var childProperty: String
  var parentEntity: OoParentWithoutPidEntityBuilder
}

internal object OoChildWithPidEntityType : EntityType<OoChildWithPidEntity, OoChildWithPidEntityBuilder>() {
  override val entityClass: Class<OoChildWithPidEntity> get() = OoChildWithPidEntity::class.java
  operator fun invoke(
    childProperty: String,
    entitySource: EntitySource,
    init: (OoChildWithPidEntityBuilder.() -> Unit)? = null,
  ): OoChildWithPidEntityBuilder {
    val builder = builder()
    builder.childProperty = childProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOoChildWithPidEntity(
  entity: OoChildWithPidEntity,
  modification: OoChildWithPidEntityBuilder.() -> Unit,
): OoChildWithPidEntity = modifyEntity(OoChildWithPidEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createOoChildWithPidEntity")
fun OoChildWithPidEntity(
  childProperty: String,
  entitySource: EntitySource,
  init: (OoChildWithPidEntityBuilder.() -> Unit)? = null,
): OoChildWithPidEntityBuilder = OoChildWithPidEntityType(childProperty, entitySource, init)
