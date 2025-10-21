// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AbstractChildWithLinkToParentEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface AbstractChildWithLinkToParentEntityBuilder<T : AbstractChildWithLinkToParentEntity> : WorkspaceEntityBuilder<T> {
  override var entitySource: EntitySource
  var data: String
}

internal object AbstractChildWithLinkToParentEntityType : EntityType<AbstractChildWithLinkToParentEntity, AbstractChildWithLinkToParentEntityBuilder<AbstractChildWithLinkToParentEntity>>() {
  override val entityClass: Class<AbstractChildWithLinkToParentEntity> get() = AbstractChildWithLinkToParentEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (AbstractChildWithLinkToParentEntityBuilder<AbstractChildWithLinkToParentEntity>.() -> Unit)? = null,
  ): AbstractChildWithLinkToParentEntityBuilder<AbstractChildWithLinkToParentEntity> {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@Parent
var AbstractChildWithLinkToParentEntityBuilder<out AbstractChildWithLinkToParentEntity>.parent: ParentWithLinkToAbstractChildBuilder?
  by WorkspaceEntity.extensionBuilder(ParentWithLinkToAbstractChild::class.java)

