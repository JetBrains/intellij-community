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
interface ModifiableCompositeAbstractEntity<T : CompositeAbstractEntity> : ModifiableWorkspaceEntity<T>, ModifiableSimpleAbstractEntity<T> {
  override var entitySource: EntitySource
  override var parentInList: ModifiableCompositeAbstractEntity<out CompositeAbstractEntity>?
  var children: List<ModifiableSimpleAbstractEntity<out SimpleAbstractEntity>>
  var parentEntity: ModifiableParentChainEntity?
}

internal object CompositeAbstractEntityType : EntityType<CompositeAbstractEntity, ModifiableCompositeAbstractEntity<CompositeAbstractEntity>>() {
  override val entityClass: Class<CompositeAbstractEntity> get() = CompositeAbstractEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableCompositeAbstractEntity<CompositeAbstractEntity>.() -> Unit)? = null,
  ): ModifiableCompositeAbstractEntity<CompositeAbstractEntity> {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
