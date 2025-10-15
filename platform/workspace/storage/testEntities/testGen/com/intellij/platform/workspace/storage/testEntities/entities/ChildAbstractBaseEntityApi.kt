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
interface ModifiableChildAbstractBaseEntity<T : ChildAbstractBaseEntity> : ModifiableWorkspaceEntity<T> {
  override var entitySource: EntitySource
  var commonData: String
  var parentEntity: ModifiableParentAbEntity
}

internal object ChildAbstractBaseEntityType : EntityType<ChildAbstractBaseEntity, ModifiableChildAbstractBaseEntity<ChildAbstractBaseEntity>>() {
  override val entityClass: Class<ChildAbstractBaseEntity> get() = ChildAbstractBaseEntity::class.java
  operator fun invoke(
    commonData: String,
    entitySource: EntitySource,
    init: (ModifiableChildAbstractBaseEntity<ChildAbstractBaseEntity>.() -> Unit)? = null,
  ): ModifiableChildAbstractBaseEntity<ChildAbstractBaseEntity> {
    val builder = builder()
    builder.commonData = commonData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
