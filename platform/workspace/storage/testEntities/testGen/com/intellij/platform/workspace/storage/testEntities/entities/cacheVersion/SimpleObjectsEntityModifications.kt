// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SimpleObjectsEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface SimpleObjectsEntityBuilder : WorkspaceEntityBuilder<SimpleObjectsEntity> {
  override var entitySource: EntitySource
  var someData: SimpleObjectsSealedClass
}

internal object SimpleObjectsEntityType : EntityType<SimpleObjectsEntity, SimpleObjectsEntityBuilder>() {
  override val entityClass: Class<SimpleObjectsEntity> get() = SimpleObjectsEntity::class.java
  operator fun invoke(
    someData: SimpleObjectsSealedClass,
    entitySource: EntitySource,
    init: (SimpleObjectsEntityBuilder.() -> Unit)? = null,
  ): SimpleObjectsEntityBuilder {
    val builder = builder()
    builder.someData = someData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySimpleObjectsEntity(
  entity: SimpleObjectsEntity,
  modification: SimpleObjectsEntityBuilder.() -> Unit,
): SimpleObjectsEntity = modifyEntity(SimpleObjectsEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSimpleObjectsEntity")
fun SimpleObjectsEntity(
  someData: SimpleObjectsSealedClass,
  entitySource: EntitySource,
  init: (SimpleObjectsEntityBuilder.() -> Unit)? = null,
): SimpleObjectsEntityBuilder = SimpleObjectsEntityType(someData, entitySource, init)
