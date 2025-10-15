// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableBaseEntity<T : BaseEntity> : ModifiableWorkspaceEntity<T> {
  override var entitySource: EntitySource
  var parentEntity: ModifiableCompositeBaseEntity<out CompositeBaseEntity>?
}

internal object BaseEntityType : EntityType<BaseEntity, ModifiableBaseEntity<BaseEntity>>() {
  override val entityClass: Class<BaseEntity> get() = BaseEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableBaseEntity<BaseEntity>.() -> Unit)? = null,
  ): ModifiableBaseEntity<BaseEntity> {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
