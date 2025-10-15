package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Open

@GeneratedCodeApiVersion(3)
interface ModifiableParentEntity<T : ParentEntity> : ModifiableWorkspaceEntity<T>, ModifiableGrandParentEntity<T> {
  override var entitySource: EntitySource
  override var data1: String
  var data2: String
}

internal object ParentEntityType : EntityType<ParentEntity, ModifiableParentEntity<ParentEntity>>() {
  override val entityClass: Class<ParentEntity> get() = ParentEntity::class.java
  operator fun invoke(
    data1: String,
    data2: String,
    entitySource: EntitySource,
    init: (ModifiableParentEntity<ParentEntity>.() -> Unit)? = null,
  ): ModifiableParentEntity<ParentEntity> {
    val builder = builder()
    builder.data1 = data1
    builder.data2 = data2
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@JvmOverloads
@JvmName("createParentEntity")
fun ParentEntity(
  data1: String,
  data2: String,
  entitySource: EntitySource,
  init: (ModifiableParentEntity<ParentEntity>.() -> Unit)? = null,
): ModifiableParentEntity = ParentEntityType(data1, data2, entitySource, init)
