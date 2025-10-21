// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("XChildEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface XChildEntityBuilder : WorkspaceEntityBuilder<XChildEntity> {
  override var entitySource: EntitySource
  var childProperty: String
  var dataClass: DataClassX?
  var parentEntity: XParentEntityBuilder
  var childChild: List<XChildChildEntityBuilder>
}

internal object XChildEntityType : EntityType<XChildEntity, XChildEntityBuilder>() {
  override val entityClass: Class<XChildEntity> get() = XChildEntity::class.java
  operator fun invoke(
    childProperty: String,
    entitySource: EntitySource,
    init: (XChildEntityBuilder.() -> Unit)? = null,
  ): XChildEntityBuilder {
    val builder = builder()
    builder.childProperty = childProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyXChildEntity(
  entity: XChildEntity,
  modification: XChildEntityBuilder.() -> Unit,
): XChildEntity = modifyEntity(XChildEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createXChildEntity")
fun XChildEntity(
  childProperty: String,
  entitySource: EntitySource,
  init: (XChildEntityBuilder.() -> Unit)? = null,
): XChildEntityBuilder = XChildEntityType(childProperty, entitySource, init)
