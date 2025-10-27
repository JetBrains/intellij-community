// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TreeMultiparentLeafEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface TreeMultiparentLeafEntityBuilder : WorkspaceEntityBuilder<TreeMultiparentLeafEntity> {
  override var entitySource: EntitySource
  var data: String
  var mainParent: TreeMultiparentRootEntityBuilder?
  var leafParent: TreeMultiparentLeafEntityBuilder?
  var children: List<TreeMultiparentLeafEntityBuilder>
}

internal object TreeMultiparentLeafEntityType : EntityType<TreeMultiparentLeafEntity, TreeMultiparentLeafEntityBuilder>() {
  override val entityClass: Class<TreeMultiparentLeafEntity> get() = TreeMultiparentLeafEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (TreeMultiparentLeafEntityBuilder.() -> Unit)? = null,
  ): TreeMultiparentLeafEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyTreeMultiparentLeafEntity(
  entity: TreeMultiparentLeafEntity,
  modification: TreeMultiparentLeafEntityBuilder.() -> Unit,
): TreeMultiparentLeafEntity = modifyEntity(TreeMultiparentLeafEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createTreeMultiparentLeafEntity")
fun TreeMultiparentLeafEntity(
  data: String,
  entitySource: EntitySource,
  init: (TreeMultiparentLeafEntityBuilder.() -> Unit)? = null,
): TreeMultiparentLeafEntityBuilder = TreeMultiparentLeafEntityType(data, entitySource, init)
