package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Open

@GeneratedCodeApiVersion(3)
interface ModifiableGrandParentEntity<T : GrandParentEntity> : ModifiableWorkspaceEntity<T> {
  override var entitySource: EntitySource
  var data1: String
}

internal object GrandParentEntityType : EntityType<GrandParentEntity, ModifiableGrandParentEntity<GrandParentEntity>>() {
  override val entityClass: Class<GrandParentEntity> get() = GrandParentEntity::class.java
  operator fun invoke(
    data1: String,
    entitySource: EntitySource,
    init: (ModifiableGrandParentEntity<GrandParentEntity>.() -> Unit)? = null,
  ): ModifiableGrandParentEntity<GrandParentEntity> {
    val builder = builder()
    builder.data1 = data1
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
