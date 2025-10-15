// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableTreeMultiparentLeafEntity : ModifiableWorkspaceEntity<TreeMultiparentLeafEntity> {
  override var entitySource: EntitySource
  var data: String
  var mainParent: ModifiableTreeMultiparentRootEntity?
  var leafParent: ModifiableTreeMultiparentLeafEntity?
  var children: List<ModifiableTreeMultiparentLeafEntity>
}

internal object TreeMultiparentLeafEntityType : EntityType<TreeMultiparentLeafEntity, ModifiableTreeMultiparentLeafEntity>() {
  override val entityClass: Class<TreeMultiparentLeafEntity> get() = TreeMultiparentLeafEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableTreeMultiparentLeafEntity.() -> Unit)? = null,
  ): ModifiableTreeMultiparentLeafEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyTreeMultiparentLeafEntity(
  entity: TreeMultiparentLeafEntity,
  modification: ModifiableTreeMultiparentLeafEntity.() -> Unit,
): TreeMultiparentLeafEntity = modifyEntity(ModifiableTreeMultiparentLeafEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createTreeMultiparentLeafEntity")
fun TreeMultiparentLeafEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableTreeMultiparentLeafEntity.() -> Unit)? = null,
): ModifiableTreeMultiparentLeafEntity = TreeMultiparentLeafEntityType(data, entitySource, init)
