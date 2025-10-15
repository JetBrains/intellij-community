// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableTreeMultiparentRootEntity : ModifiableWorkspaceEntity<TreeMultiparentRootEntity> {
  override var entitySource: EntitySource
  var data: String
  var children: List<ModifiableTreeMultiparentLeafEntity>
}

internal object TreeMultiparentRootEntityType : EntityType<TreeMultiparentRootEntity, ModifiableTreeMultiparentRootEntity>() {
  override val entityClass: Class<TreeMultiparentRootEntity> get() = TreeMultiparentRootEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableTreeMultiparentRootEntity.() -> Unit)? = null,
  ): ModifiableTreeMultiparentRootEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyTreeMultiparentRootEntity(
  entity: TreeMultiparentRootEntity,
  modification: ModifiableTreeMultiparentRootEntity.() -> Unit,
): TreeMultiparentRootEntity = modifyEntity(ModifiableTreeMultiparentRootEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createTreeMultiparentRootEntity")
fun TreeMultiparentRootEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableTreeMultiparentRootEntity.() -> Unit)? = null,
): ModifiableTreeMultiparentRootEntity = TreeMultiparentRootEntityType(data, entitySource, init)
