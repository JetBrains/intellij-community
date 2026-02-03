// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AbstractParentEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface AbstractParentEntityBuilder<T : AbstractParentEntity> : WorkspaceEntityBuilder<T> {
  override var entitySource: EntitySource
  var data: String
  var child: ChildWithExtensionParentBuilder?
}

internal object AbstractParentEntityType : EntityType<AbstractParentEntity, AbstractParentEntityBuilder<AbstractParentEntity>>() {
  override val entityClass: Class<AbstractParentEntity> get() = AbstractParentEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (AbstractParentEntityBuilder<AbstractParentEntity>.() -> Unit)? = null,
  ): AbstractParentEntityBuilder<AbstractParentEntity> {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
