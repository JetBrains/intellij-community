// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AbstractChildEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface AbstractChildEntityBuilder<T : AbstractChildEntity> : WorkspaceEntityBuilder<T> {
  override var entitySource: EntitySource
  var data: String
  var parent: ParentWithExtensionEntityBuilder
}

internal object AbstractChildEntityType : EntityType<AbstractChildEntity, AbstractChildEntityBuilder<AbstractChildEntity>>() {
  override val entityClass: Class<AbstractChildEntity> get() = AbstractChildEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (AbstractChildEntityBuilder<AbstractChildEntity>.() -> Unit)? = null,
  ): AbstractChildEntityBuilder<AbstractChildEntity> {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
