// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ParentEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ParentEntityBuilder : WorkspaceEntityBuilder<ParentEntity> {
  override var entitySource: EntitySource
  var parentData: String
  var child: ChildEntityBuilder?
}

internal object ParentEntityType : EntityType<ParentEntity, ParentEntityBuilder>() {
  override val entityClass: Class<ParentEntity> get() = ParentEntity::class.java
  operator fun invoke(
    parentData: String,
    entitySource: EntitySource,
    init: (ParentEntityBuilder.() -> Unit)? = null,
  ): ParentEntityBuilder {
    val builder = builder()
    builder.parentData = parentData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentEntity(
  entity: ParentEntity,
  modification: ParentEntityBuilder.() -> Unit,
): ParentEntity = modifyEntity(ParentEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentEntity")
fun ParentEntity(
  parentData: String,
  entitySource: EntitySource,
  init: (ParentEntityBuilder.() -> Unit)? = null,
): ParentEntityBuilder = ParentEntityType(parentData, entitySource, init)
