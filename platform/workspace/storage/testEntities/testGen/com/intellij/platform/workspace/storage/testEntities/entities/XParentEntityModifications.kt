// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("XParentEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface XParentEntityBuilder : WorkspaceEntityBuilder<XParentEntity> {
  override var entitySource: EntitySource
  var parentProperty: String
  var children: List<XChildEntityBuilder>
  var optionalChildren: List<XChildWithOptionalParentEntityBuilder>
  var childChild: List<XChildChildEntityBuilder>
}

internal object XParentEntityType : EntityType<XParentEntity, XParentEntityBuilder>() {
  override val entityClass: Class<XParentEntity> get() = XParentEntity::class.java
  operator fun invoke(
    parentProperty: String,
    entitySource: EntitySource,
    init: (XParentEntityBuilder.() -> Unit)? = null,
  ): XParentEntityBuilder {
    val builder = builder()
    builder.parentProperty = parentProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyXParentEntity(
  entity: XParentEntity,
  modification: XParentEntityBuilder.() -> Unit,
): XParentEntity = modifyEntity(XParentEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createXParentEntity")
fun XParentEntity(
  parentProperty: String,
  entitySource: EntitySource,
  init: (XParentEntityBuilder.() -> Unit)? = null,
): XParentEntityBuilder = XParentEntityType(parentProperty, entitySource, init)
