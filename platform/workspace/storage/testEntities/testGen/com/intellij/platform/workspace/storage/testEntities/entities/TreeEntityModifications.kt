// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TreeEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface TreeEntityBuilder : WorkspaceEntityBuilder<TreeEntity> {
  override var entitySource: EntitySource
  var data: String
  var children: List<TreeEntityBuilder>
  var parentEntity: TreeEntityBuilder?
}

internal object TreeEntityType : EntityType<TreeEntity, TreeEntityBuilder>() {
  override val entityClass: Class<TreeEntity> get() = TreeEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (TreeEntityBuilder.() -> Unit)? = null,
  ): TreeEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyTreeEntity(
  entity: TreeEntity,
  modification: TreeEntityBuilder.() -> Unit,
): TreeEntity = modifyEntity(TreeEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createTreeEntity")
fun TreeEntity(
  data: String,
  entitySource: EntitySource,
  init: (TreeEntityBuilder.() -> Unit)? = null,
): TreeEntityBuilder = TreeEntityType(data, entitySource, init)
