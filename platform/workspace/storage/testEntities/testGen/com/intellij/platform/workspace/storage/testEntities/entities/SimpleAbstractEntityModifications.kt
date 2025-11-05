// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SimpleAbstractEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface SimpleAbstractEntityBuilder<T : SimpleAbstractEntity> : WorkspaceEntityBuilder<T> {
  override var entitySource: EntitySource
  var parentInList: CompositeAbstractEntityBuilder<out CompositeAbstractEntity>?
}

internal object SimpleAbstractEntityType : EntityType<SimpleAbstractEntity, SimpleAbstractEntityBuilder<SimpleAbstractEntity>>() {
  override val entityClass: Class<SimpleAbstractEntity> get() = SimpleAbstractEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (SimpleAbstractEntityBuilder<SimpleAbstractEntity>.() -> Unit)? = null,
  ): SimpleAbstractEntityBuilder<SimpleAbstractEntity> {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
