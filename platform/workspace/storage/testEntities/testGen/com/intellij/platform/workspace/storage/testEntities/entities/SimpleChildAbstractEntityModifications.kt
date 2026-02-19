// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SimpleChildAbstractEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface SimpleChildAbstractEntityBuilder : WorkspaceEntityBuilder<SimpleChildAbstractEntity>, SimpleAbstractEntityBuilder<SimpleChildAbstractEntity> {
  override var entitySource: EntitySource
  override var parentInList: CompositeAbstractEntityBuilder<out CompositeAbstractEntity>?
}

internal object SimpleChildAbstractEntityType : EntityType<SimpleChildAbstractEntity, SimpleChildAbstractEntityBuilder>() {
  override val entityClass: Class<SimpleChildAbstractEntity> get() = SimpleChildAbstractEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (SimpleChildAbstractEntityBuilder.() -> Unit)? = null,
  ): SimpleChildAbstractEntityBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySimpleChildAbstractEntity(
  entity: SimpleChildAbstractEntity,
  modification: SimpleChildAbstractEntityBuilder.() -> Unit,
): SimpleChildAbstractEntity = modifyEntity(SimpleChildAbstractEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSimpleChildAbstractEntity")
fun SimpleChildAbstractEntity(
  entitySource: EntitySource,
  init: (SimpleChildAbstractEntityBuilder.() -> Unit)? = null,
): SimpleChildAbstractEntityBuilder = SimpleChildAbstractEntityType(entitySource, init)
