// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OoChildAlsoWithPidEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface OoChildAlsoWithPidEntityBuilder : WorkspaceEntityBuilder<OoChildAlsoWithPidEntity> {
  override var entitySource: EntitySource
  var childProperty: String
  var parentEntity: OoParentWithPidEntityBuilder
}

internal object OoChildAlsoWithPidEntityType : EntityType<OoChildAlsoWithPidEntity, OoChildAlsoWithPidEntityBuilder>() {
  override val entityClass: Class<OoChildAlsoWithPidEntity> get() = OoChildAlsoWithPidEntity::class.java
  operator fun invoke(
    childProperty: String,
    entitySource: EntitySource,
    init: (OoChildAlsoWithPidEntityBuilder.() -> Unit)? = null,
  ): OoChildAlsoWithPidEntityBuilder {
    val builder = builder()
    builder.childProperty = childProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOoChildAlsoWithPidEntity(
  entity: OoChildAlsoWithPidEntity,
  modification: OoChildAlsoWithPidEntityBuilder.() -> Unit,
): OoChildAlsoWithPidEntity = modifyEntity(OoChildAlsoWithPidEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createOoChildAlsoWithPidEntity")
fun OoChildAlsoWithPidEntity(
  childProperty: String,
  entitySource: EntitySource,
  init: (OoChildAlsoWithPidEntityBuilder.() -> Unit)? = null,
): OoChildAlsoWithPidEntityBuilder = OoChildAlsoWithPidEntityType(childProperty, entitySource, init)
