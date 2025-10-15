// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableTreeEntity : ModifiableWorkspaceEntity<TreeEntity> {
  override var entitySource: EntitySource
  var data: String
  var children: List<ModifiableTreeEntity>
  var parentEntity: ModifiableTreeEntity?
}

internal object TreeEntityType : EntityType<TreeEntity, ModifiableTreeEntity>() {
  override val entityClass: Class<TreeEntity> get() = TreeEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableTreeEntity.() -> Unit)? = null,
  ): ModifiableTreeEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyTreeEntity(
  entity: TreeEntity,
  modification: ModifiableTreeEntity.() -> Unit,
): TreeEntity = modifyEntity(ModifiableTreeEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createTreeEntity")
fun TreeEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableTreeEntity.() -> Unit)? = null,
): ModifiableTreeEntity = TreeEntityType(data, entitySource, init)
