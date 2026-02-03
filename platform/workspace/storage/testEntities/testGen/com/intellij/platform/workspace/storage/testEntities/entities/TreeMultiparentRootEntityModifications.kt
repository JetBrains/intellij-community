// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TreeMultiparentRootEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface TreeMultiparentRootEntityBuilder : WorkspaceEntityBuilder<TreeMultiparentRootEntity> {
  override var entitySource: EntitySource
  var data: String
  var children: List<TreeMultiparentLeafEntityBuilder>
}

internal object TreeMultiparentRootEntityType : EntityType<TreeMultiparentRootEntity, TreeMultiparentRootEntityBuilder>() {
  override val entityClass: Class<TreeMultiparentRootEntity> get() = TreeMultiparentRootEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (TreeMultiparentRootEntityBuilder.() -> Unit)? = null,
  ): TreeMultiparentRootEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyTreeMultiparentRootEntity(
  entity: TreeMultiparentRootEntity,
  modification: TreeMultiparentRootEntityBuilder.() -> Unit,
): TreeMultiparentRootEntity = modifyEntity(TreeMultiparentRootEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createTreeMultiparentRootEntity")
fun TreeMultiparentRootEntity(
  data: String,
  entitySource: EntitySource,
  init: (TreeMultiparentRootEntityBuilder.() -> Unit)? = null,
): TreeMultiparentRootEntityBuilder = TreeMultiparentRootEntityType(data, entitySource, init)
