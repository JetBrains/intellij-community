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
interface ModifiableAbstractChildEntity<T : AbstractChildEntity> : ModifiableWorkspaceEntity<T> {
  override var entitySource: EntitySource
  var data: String
  var parent: ModifiableParentWithExtensionEntity
}

internal object AbstractChildEntityType : EntityType<AbstractChildEntity, ModifiableAbstractChildEntity<AbstractChildEntity>>() {
  override val entityClass: Class<AbstractChildEntity> get() = AbstractChildEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableAbstractChildEntity<AbstractChildEntity>.() -> Unit)? = null,
  ): ModifiableAbstractChildEntity<AbstractChildEntity> {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
