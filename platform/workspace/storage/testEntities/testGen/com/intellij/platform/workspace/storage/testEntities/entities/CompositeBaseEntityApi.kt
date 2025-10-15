// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableCompositeBaseEntity<T : CompositeBaseEntity> : ModifiableWorkspaceEntity<T>, ModifiableBaseEntity<T> {
  override var entitySource: EntitySource
  override var parentEntity: ModifiableCompositeBaseEntity<out CompositeBaseEntity>?
  var children: List<ModifiableBaseEntity<out BaseEntity>>
  var parent: ModifiableHeadAbstractionEntity?
}

internal object CompositeBaseEntityType : EntityType<CompositeBaseEntity, ModifiableCompositeBaseEntity<CompositeBaseEntity>>() {
  override val entityClass: Class<CompositeBaseEntity> get() = CompositeBaseEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableCompositeBaseEntity<CompositeBaseEntity>.() -> Unit)? = null,
  ): ModifiableCompositeBaseEntity<CompositeBaseEntity> {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
