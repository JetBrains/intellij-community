// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableSimpleAbstractEntity<T : SimpleAbstractEntity> : ModifiableWorkspaceEntity<T> {
  override var entitySource: EntitySource
  var parentInList: ModifiableCompositeAbstractEntity<out CompositeAbstractEntity>?
}

internal object SimpleAbstractEntityType : EntityType<SimpleAbstractEntity, ModifiableSimpleAbstractEntity<SimpleAbstractEntity>>() {
  override val entityClass: Class<SimpleAbstractEntity> get() = SimpleAbstractEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableSimpleAbstractEntity<SimpleAbstractEntity>.() -> Unit)? = null,
  ): ModifiableSimpleAbstractEntity<SimpleAbstractEntity> {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
