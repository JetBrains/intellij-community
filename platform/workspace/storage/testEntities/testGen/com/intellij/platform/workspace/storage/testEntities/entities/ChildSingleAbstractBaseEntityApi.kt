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
interface ModifiableChildSingleAbstractBaseEntity<T : ChildSingleAbstractBaseEntity> : ModifiableWorkspaceEntity<T> {
  override var entitySource: EntitySource
  var commonData: String
  var parentEntity: ModifiableParentSingleAbEntity
}

internal object ChildSingleAbstractBaseEntityType : EntityType<ChildSingleAbstractBaseEntity, ModifiableChildSingleAbstractBaseEntity<ChildSingleAbstractBaseEntity>>() {
  override val entityClass: Class<ChildSingleAbstractBaseEntity> get() = ChildSingleAbstractBaseEntity::class.java
  operator fun invoke(
    commonData: String,
    entitySource: EntitySource,
    init: (ModifiableChildSingleAbstractBaseEntity<ChildSingleAbstractBaseEntity>.() -> Unit)? = null,
  ): ModifiableChildSingleAbstractBaseEntity<ChildSingleAbstractBaseEntity> {
    val builder = builder()
    builder.commonData = commonData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
