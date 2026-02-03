// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ParentMultipleEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ParentMultipleEntityBuilder : WorkspaceEntityBuilder<ParentMultipleEntity> {
  override var entitySource: EntitySource
  var parentData: String
  var children: List<ChildMultipleEntityBuilder>
}

internal object ParentMultipleEntityType : EntityType<ParentMultipleEntity, ParentMultipleEntityBuilder>() {
  override val entityClass: Class<ParentMultipleEntity> get() = ParentMultipleEntity::class.java
  operator fun invoke(
    parentData: String,
    entitySource: EntitySource,
    init: (ParentMultipleEntityBuilder.() -> Unit)? = null,
  ): ParentMultipleEntityBuilder {
    val builder = builder()
    builder.parentData = parentData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentMultipleEntity(
  entity: ParentMultipleEntity,
  modification: ParentMultipleEntityBuilder.() -> Unit,
): ParentMultipleEntity = modifyEntity(ParentMultipleEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentMultipleEntity")
fun ParentMultipleEntity(
  parentData: String,
  entitySource: EntitySource,
  init: (ParentMultipleEntityBuilder.() -> Unit)? = null,
): ParentMultipleEntityBuilder = ParentMultipleEntityType(parentData, entitySource, init)
