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
interface ModifiableAbstractChildWithLinkToParentEntity<T : AbstractChildWithLinkToParentEntity> : ModifiableWorkspaceEntity<T> {
  override var entitySource: EntitySource
  var data: String
}

internal object AbstractChildWithLinkToParentEntityType : EntityType<AbstractChildWithLinkToParentEntity, ModifiableAbstractChildWithLinkToParentEntity<AbstractChildWithLinkToParentEntity>>() {
  override val entityClass: Class<AbstractChildWithLinkToParentEntity> get() = AbstractChildWithLinkToParentEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableAbstractChildWithLinkToParentEntity<AbstractChildWithLinkToParentEntity>.() -> Unit)? = null,
  ): ModifiableAbstractChildWithLinkToParentEntity<AbstractChildWithLinkToParentEntity> {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@Parent
var ModifiableAbstractChildWithLinkToParentEntity<out AbstractChildWithLinkToParentEntity>.parent: ModifiableParentWithLinkToAbstractChild?
  by WorkspaceEntity.extensionBuilder(ParentWithLinkToAbstractChild::class.java)

