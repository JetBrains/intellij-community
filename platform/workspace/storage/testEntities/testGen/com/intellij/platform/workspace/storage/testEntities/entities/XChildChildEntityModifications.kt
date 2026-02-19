// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("XChildChildEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface XChildChildEntityBuilder : WorkspaceEntityBuilder<XChildChildEntity> {
  override var entitySource: EntitySource
  var parent1: XParentEntityBuilder
  var parent2: XChildEntityBuilder
}

internal object XChildChildEntityType : EntityType<XChildChildEntity, XChildChildEntityBuilder>() {
  override val entityClass: Class<XChildChildEntity> get() = XChildChildEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (XChildChildEntityBuilder.() -> Unit)? = null,
  ): XChildChildEntityBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyXChildChildEntity(
  entity: XChildChildEntity,
  modification: XChildChildEntityBuilder.() -> Unit,
): XChildChildEntity = modifyEntity(XChildChildEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createXChildChildEntity")
fun XChildChildEntity(
  entitySource: EntitySource,
  init: (XChildChildEntityBuilder.() -> Unit)? = null,
): XChildChildEntityBuilder = XChildChildEntityType(entitySource, init)
