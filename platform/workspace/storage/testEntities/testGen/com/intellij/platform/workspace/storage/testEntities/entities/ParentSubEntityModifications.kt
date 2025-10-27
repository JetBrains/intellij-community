// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ParentSubEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ParentSubEntityBuilder : WorkspaceEntityBuilder<ParentSubEntity> {
  override var entitySource: EntitySource
  var parentData: String
  var child: ChildSubEntityBuilder?
}

internal object ParentSubEntityType : EntityType<ParentSubEntity, ParentSubEntityBuilder>() {
  override val entityClass: Class<ParentSubEntity> get() = ParentSubEntity::class.java
  operator fun invoke(
    parentData: String,
    entitySource: EntitySource,
    init: (ParentSubEntityBuilder.() -> Unit)? = null,
  ): ParentSubEntityBuilder {
    val builder = builder()
    builder.parentData = parentData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentSubEntity(
  entity: ParentSubEntity,
  modification: ParentSubEntityBuilder.() -> Unit,
): ParentSubEntity = modifyEntity(ParentSubEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentSubEntity")
fun ParentSubEntity(
  parentData: String,
  entitySource: EntitySource,
  init: (ParentSubEntityBuilder.() -> Unit)? = null,
): ParentSubEntityBuilder = ParentSubEntityType(parentData, entitySource, init)
